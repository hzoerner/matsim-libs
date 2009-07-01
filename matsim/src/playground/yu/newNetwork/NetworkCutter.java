/* *********************************************************************** *
 * project: org.matsim.*
 * NetworkCutter.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 * 
 */
package playground.yu.newNetwork;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.matsim.api.basic.v01.Coord;
import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.experimental.population.PlanElement;
import org.matsim.core.api.experimental.population.Population;
import org.matsim.core.api.network.Link;
import org.matsim.core.api.network.Node;
import org.matsim.core.api.population.NetworkRoute;
import org.matsim.core.api.population.Route;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.utils.misc.ArgumentParser;

/**
 * ensures only links in a rectangle that could be passed over by the plans can
 * be retained in the network.
 * 
 * @author yu
 * 
 */
public class NetworkCutter {
	private double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0,
			maxY = 0;

	private void printUsage() {
		System.out.println();
		System.out.println("NetworkCutter");
		System.out
				.println("Reads a network-file and \"cut\" it. Currently, it performs the following");
		System.out
				.println("steps to ensure a network is suited for simulation:");
		System.out
				.println(" - ensure only links in a rectangle that could be passed over by the plans can be retained in the network.");
		System.out.println();
		System.out
				.println("usage: NetworkCutter [OPTIONS] input-network-file input-plans-file output-network-file");
		System.out.println();
		System.out.println("Options:");
		System.out.println("-h, --help:     Displays this message.");
		System.out.println();
		System.out.println("----------------");
		System.out.println("2008, matsim.org");
		System.out.println();
	}

	/**
	 * Runs the network cutting algorithms over the network and the plans read
	 * in from <code>inputNetworkFile</code> and <code>plansFile</code>, writes
	 * the resulting ("cleaned") network to the specified file.
	 * 
	 * @param inputNetworkFile
	 *            filename of the network to be handled
	 * @param plansFile
	 *            filenmae of the plans to be handled
	 * @param outputNetworkFile
	 *            filename where to write the cleaned network to
	 */
	public void run(final String inputNetworkFile, final String plansFile,
			final String outputNetworkFile) {
		final NetworkLayer network = new NetworkLayer();
		new MatsimNetworkReader(network).readFile(inputNetworkFile);

		final Population pop = new PopulationImpl();
		new MatsimPopulationReader(pop, network).readFile(plansFile);

		run(network, pop);

		final NetworkWriter network_writer = new NetworkWriter(network,
				outputNetworkFile);
		network_writer.write();
	}

	public void run(NetworkLayer net, Population pop) {
		for (PersonImpl person : pop.getPersons().values())
			for (PlanImpl plan : person.getPlans())
				for (PlanElement pe : plan.getPlanElements())
					if (pe instanceof ActivityImpl)
						resetBoundary(((ActivityImpl) pe).getCoord());
					else {
						Route route = ((LegImpl) pe).getRoute();
						if (route != null && (route instanceof NetworkRoute))
							resetBoundary((NetworkRoute) route, net);
					}
		Set<Link> links = new HashSet<Link>();
		links.addAll(net.getLinks().values());
		for (Link link : links)
			if (!inside(link))
				net.removeLink(link);
		Set<Node> nodes = new HashSet<Node>();
		nodes.addAll(net.getNodes().values());
		for (Node node : nodes)
			if (!inside(node))
				net.removeNode(node);
	}

	// not perfect, but it's enough to test
	private boolean inside(Link link) {
		return inside(link.getFromNode()) || inside(link.getToNode());
	}

	private boolean inside(Node node) {
		Coord crd = node.getCoord();
		double x = crd.getX();
		double y = crd.getY();
		return x >= minX - 1000 && x <= maxX + 1000 && y >= minY - 1000
				&& y <= maxY + 1000;
	}

	private void resetBoundary(NetworkRoute route, NetworkLayer net) {
		for (Id linkId : route.getLinkIds()) {
			resetBoundary(net.getLink(linkId));
		}
		resetBoundary(route.getStartLink());
		resetBoundary(route.getEndLink());
	}

	private void resetBoundary(Link link) {
		resetBoundary(link.getFromNode().getCoord());
		resetBoundary(link.getToNode().getCoord());
	}

	private void resetBoundary(Coord crd) {
		double x = crd.getX();
		double y = crd.getY();
		if (x < minX)
			minX = x;
		if (x > maxX)
			maxX = x;
		if (y < minY)
			minY = y;
		if (y > maxY)
			maxY = y;
	}

	/**
	 * Runs the network cutting algorithms over the network read in from the
	 * argument list, and writing the resulting network out to a file again
	 * 
	 * @param args
	 *            <code>args[0]</code> filename of the network to be handled,
	 *            <code>args[1]</code> filename of the population to be
	 *            simulated, <code>args[2]</code> filename where to write the
	 *            cleaned network to
	 */
	public void run(final String[] args) {
		if (args.length == 0) {
			System.out.println("Too few arguments.");
			printUsage();
			throw new RuntimeException("Too few arguments.");
		}
		Iterator<String> argIter = new ArgumentParser(args).iterator();
		String arg = argIter.next();
		if (arg.equals("-h") || arg.equals("--help")) {
			printUsage();
			System.exit(0);
		} else {
			String inputFile = arg;
			if (!argIter.hasNext()) {
				System.out.println("Too few arguments.");
				printUsage();
				throw new RuntimeException("Too few arguments.");
			}

			String plansFile = argIter.next();
			if (!argIter.hasNext()) {
				System.out.println("Too few arguments.");
				printUsage();
				throw new RuntimeException("Too few arguments.");
			}

			String outputFile = argIter.next();
			if (argIter.hasNext()) {
				System.out.println("Too many arguments.");
				printUsage();
				throw new RuntimeException("Too many arguments.");
			}

			run(inputFile, plansFile, outputFile);
		}
	}

	public static void main(String[] args) {
		new NetworkCutter()
				.run(new String[] {
						"../schweiz-ivtch-SVN/baseCase/network/ivtch-osm.xml",
						"test/input/playground/yu/test/ChangeLegModeWithParkLocationTest/testLegChainModes/plans1.xml",
						"test/input/playground/yu/test/ChangeLegModeWithParkLocationTest/testLegChainModes/network.xml.gz" });
	}

}
