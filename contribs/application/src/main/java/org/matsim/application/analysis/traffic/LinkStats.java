package org.matsim.application.analysis.traffic;

import com.beust.jcommander.internal.Lists;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CsvOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "link-stats", description = "Compute aggregated link statistics, like volume, travel time and congestion")
public class LinkStats implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(LinkStats.class);

	@CommandLine.Option(names = "--network", description = "Input network file", required = true)
	private Path network;

	@CommandLine.Option(names = "--events", description = "Input event file", required = true)
	private Path events;

	@CommandLine.Option(names = "--output", description = "Path for output tsv", required = true)
	private Path output;

	@CommandLine.Option(names = "--output-congestion", description = "Path for output csv for congestion", required = true)
	private Path outputCongestion;

	@CommandLine.Option(names = "--output-velocities", description = "Path for output csv for avg link speeds")
	private Path outputVelocities;

	//@CommandLine.Option(names = "--link-to-link", description = "Also calculate link to link travel times", defaultValue = "false")
	//private boolean l2l;

	@CommandLine.Option(names = "--max-time", description = "Maximum time used in aggregation", defaultValue = "86399")
	private int maxTime;

	@CommandLine.Option(names = "--interval", description = "Number of seconds per time slice", defaultValue = "900")
	private int timeSlice;

	@CommandLine.Option(names = "--min-capacity", description = "Minimum capacity of the road (link) for it to" +
			" be considered (i.e. only include the major roads in the network in the analysis)", defaultValue = "1200")
	private int minCapacity;

	@CommandLine.Mixin
	private ShpOptions shp = new ShpOptions();

	@CommandLine.Mixin
	private CsvOptions csv = new CsvOptions();

	public static void main(String[] args) {
		new LinkStats().execute(args);
	}

	@SuppressWarnings("JavaNCSS")
	@Override
	public Integer call() throws Exception {

		EventsManager eventsManager = EventsUtils.createEventsManager();

		Network network = NetworkUtils.readNetwork(this.network.toString());

		TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(network);
		builder.setTimeslice(timeSlice);
		builder.setMaxTime(maxTime);
		builder.setCalculateLinkTravelTimes(true);
		builder.setCalculateLinkToLinkTravelTimes(false);

		TravelTimeCalculator calculator = builder.build();
		eventsManager.addHandler(calculator);

		VolumesAnalyzer volume = new VolumesAnalyzer(timeSlice, maxTime, network, true);
		eventsManager.addHandler(volume);

		eventsManager.initProcessing();

		EventsUtils.readEvents(eventsManager, events.toString());

		eventsManager.finishProcessing();

		log.info("Writing stats to {}", output);

		List<String> header = Lists.newArrayList("linkId", "time", "avgTravelTime");

		Set<String> modes = volume.getModes();

		for (String mode : modes) {
			header.add("vol_" + mode);
		}

		TravelTime tt = calculator.getLinkTravelTimes();

		ShpOptions.Index index = shp.isDefined() ? shp.createIndex(ProjectionUtils.getCRS(network), "_") : null;

		int n = volume.getVolumesArraySize();

		try (CSVPrinter writer = csv.createPrinter(output)) {

			writer.printRecord(header);

			for (Link link : network.getLinks().values()) {

				if (!considerLink(link, index))
					continue;

				for (int i = 0; i < n; i++) {

					int time = i * timeSlice;
					double avgTt = tt.getLinkTravelTime(link, time, null, null);

					List<Object> row = Lists.newArrayList(link.getId(), time, avgTt);

					for (String mode : modes) {
						int[] vol = volume.getVolumesForLink(link.getId(), mode);

						// can be null (if no vehicle uses this link?)
						if (vol == null)
							row.add(0);
						else
							row.add(vol[i]);
					}

					writer.printRecord(row);
				}
			}
		}

		double networkCongestionIndex = 0;
		double totalLength = 0;
		Int2ObjectMap<DoubleList> networkSpeedRatiosMap = new Int2ObjectOpenHashMap<>();

		//Store avg velocities per link
		Map<Id<Link>, Int2DoubleMap> avgSpeedPerTimeSliceAndLink = new HashMap<>();

		// calculate congestion index according to
		// A Traffic Congestion Assessment Method for Urban Road Networks Based on Speed Performance Index
		// Feifei He, Xuedong Yan*, Yang Liu, Lu Ma

		try (CSVPrinter writer = csv.createPrinter(outputCongestion)) {

			List<String> titleRow = new ArrayList<>();
			titleRow.add("link_id");
			titleRow.add("congestion_index");
			titleRow.add("average_daily_speed");
			for (int i = 0; i < maxTime; i += timeSlice) {
				networkSpeedRatiosMap.put(i, new DoubleArrayList());
				titleRow.add(Integer.toString(i));
			}
			writer.printRecord(titleRow);

			for (Link link : network.getLinks().values()) {

				if (!considerLink(link, index))
					continue;

				DoubleList linksSpeedRatios = new DoubleArrayList();

				Int2DoubleOpenHashMap velocitiesOnLink = new Int2DoubleOpenHashMap();

				double counter = 0;
				double congestedPeriods = 0;
				for (int t = 0; t < 86400; t += timeSlice) {
					double freeSpeedTravelTime = Math.floor(link.getLength() / link.getFreespeed()) + 1;
					double actualTravelTime = tt.getLinkTravelTime(link, t, null, null);

					double actualSpeed = Math.round(link.getLength() / actualTravelTime);
					velocitiesOnLink.put(t, actualSpeed);

					double speedRatio = freeSpeedTravelTime / actualTravelTime;
					if (speedRatio > 1) {
						speedRatio = 1;
					}
					networkSpeedRatiosMap.get(t).add(speedRatio);
					linksSpeedRatios.add(speedRatio);
					counter++;
					if (speedRatio <= 0.5) {
						congestedPeriods++;
					}
				}
				avgSpeedPerTimeSliceAndLink.put(link.getId(), velocitiesOnLink);

				double linkDailyAverageSpeed = linksSpeedRatios.doubleStream().average().orElse(-1);
				double linkCongestionIndex = linkDailyAverageSpeed * (1 - congestedPeriods / counter);

				List<String> outputRow = new ArrayList<>();
				outputRow.add(link.getId().toString());
				outputRow.add(Double.toString(linkCongestionIndex));
				outputRow.add(Double.toString(linkDailyAverageSpeed));
				outputRow.addAll(linksSpeedRatios.doubleStream().mapToObj(Double::toString).collect(Collectors.toList()));
				writer.printRecord(outputRow);

				networkCongestionIndex += linkCongestionIndex * link.getLength();
				totalLength += link.getLength();

			}

			// final row (whole network)
			networkCongestionIndex = networkCongestionIndex / totalLength;
			DoubleList networkSpeedRatios = new DoubleArrayList();
			for (int t : networkSpeedRatiosMap.keySet()) {
				double averageSpeedRatioForTimePeriod = networkSpeedRatiosMap.get(t).doubleStream().average().orElse(-1);
				networkSpeedRatios.add(averageSpeedRatioForTimePeriod);
			}

			List<String> lastRow = new ArrayList<>();
			lastRow.add("full_network");
			lastRow.add(Double.toString(networkCongestionIndex));
			lastRow.add(Double.toString(networkSpeedRatios.doubleStream().average().orElse(-1)));
			lastRow.addAll(networkSpeedRatios.doubleStream().mapToObj(Double::toString).collect(Collectors.toList()));
			writer.printRecord(lastRow);
		}

		//print only if output path is supplied. This may avoid many crashs in existing makefiles.
		if (outputVelocities == null)
			return 0;

		try (CSVPrinter printer = csv.createPrinter(outputVelocities)) {
			printer.printRecord(List.of("linkId", "time", "avg_speed"));

			for (Map.Entry<Id<Link>, Int2DoubleMap> linkEntry : avgSpeedPerTimeSliceAndLink.entrySet()) {
				String linkId = linkEntry.getKey().toString();
				for (Int2DoubleMap.Entry timeEntry : linkEntry.getValue().int2DoubleEntrySet()) {
					printer.print(linkId);
					printer.print(timeEntry.getIntKey());
					printer.print(timeEntry.getDoubleValue());
					printer.println();
				}
			}
		}

		return 0;
	}

	private boolean considerLink(Link link, ShpOptions.Index index) {
		if (index != null && !index.contains(link.getCoord()))
			return false;

		return (link.getCapacity() >= minCapacity && link.getAllowedModes().contains(TransportMode.car));
	}

}
