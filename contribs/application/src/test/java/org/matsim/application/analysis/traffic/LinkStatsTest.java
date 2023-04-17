package org.matsim.application.analysis.traffic;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class LinkStatsTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void testLinkStatsWithAverageVelocityOutput() {

		String configPath = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("berlin"), "config.xml").toString();
		Config config = ConfigUtils.loadConfig(configPath);
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		config.controler().setRunId("test");
		Scenario scenario = ScenarioUtils.createScenario(config);
		new Controler(scenario).run();

		Logger logger = LogManager.getLogger(LinkStatsTest.class);

		String outputVelocities = "C:\\Users\\ACER\\Desktop\\Uni\\VSP\\Berlin_6_x\\count-files\\test.output_real_velocities.csv";

		String networkPath = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("berlin"), "network.xml.gz").toString().replace("file:/", "");

		String[] args = {
				"--events=" + utils.getOutputDirectory() + "test.output_events.xml.gz",
				"--network=" + networkPath,
				"--output=" + utils.getOutputDirectory() + "test.output_linkstats.csv",
				"--output-congestion=" + utils.getOutputDirectory() + "test.output_congestion.csv",
				"--output-velocities=" + outputVelocities,
				"--interval=3600",
		};

		new LinkStats().execute(args);

		try{
			List<CSVRecord> records = CSVFormat.DEFAULT.parse(new BufferedReader(new FileReader(outputVelocities))).getRecords();
			Assert.assertNotEquals(1, records.size());
		} catch (IOException e){
			logger.warn(e.getMessage());
		}
	}
}
