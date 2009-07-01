/* *********************************************************************** *
 * project: org.matsim.*
 * SensibleParkLocation.java
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
package playground.yu.analysis;

import java.util.List;

import org.matsim.api.basic.v01.TransportMode;
import org.matsim.core.api.experimental.population.PlanElement;
import org.matsim.core.api.experimental.population.Population;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.population.algorithms.AbstractPersonAlgorithm;
import org.matsim.population.algorithms.PlanAlgorithm;

import playground.yu.test.ChangeLegModeWithParkLocation.ParkLocation;
import playground.yu.utils.io.SimpleWriter;

/**
 * @author yu
 * 
 */
public class SensibleParkLocation extends AbstractPersonAlgorithm implements
		PlanAlgorithm {
	private int n = 0, f = 0;
	static int nullCnt = 0;
	private static SimpleWriter writer = null;

	public SensibleParkLocation(String outputFilename) {
		writer = new SimpleWriter(outputFilename);
	}

	private static boolean checkParkSensible(PlanImpl plan) {
		int carLegCnt = 0;
		ParkLocation origPark = null, lastNextPark = null;

		List<PlanElement> pes = plan.getPlanElements();

		for (int i = 1; i < pes.size() - 1; i += 2) {

			LegImpl leg = (LegImpl) pes.get(i);
			if (leg.getMode().equals(TransportMode.car)) {
				ParkLocation prePark = new ParkLocation(plan
						.getPreviousActivity(leg));
				ParkLocation nextPark = new ParkLocation(plan
						.getNextActivity(leg));

				if (carLegCnt == 0)
					origPark = prePark;
				else if (!prePark.equals(lastNextPark))
					return false;

				lastNextPark = nextPark;
				carLegCnt++;
			}
		}

		if (origPark == null && lastNextPark == null) {
			nullCnt++;
			System.out.println(getPlanElementsPattern(plan));
			return true;
		}
		return origPark.equals(lastNextPark);
	}

	@Override
	public void run(PersonImpl person) {
		for (PlanImpl plan : person.getPlans())
			run(plan);
		n++;
	}

	public void run(PlanImpl plan) {
		List<PlanElement> pes = plan.getPlanElements();

		for (int i = 0; i < pes.size(); i += 2)
			if (((ActivityImpl) pes.get(i)).getType().equals("tta"))
				return;

		if (!checkParkSensible(plan)) {
			writer.writeln(getPlanElementsPattern(plan));
			writer.writeln("---------------------------");
			writer.flush();
			f++;
		}
	}

	private static String getPlanElementsPattern(PlanImpl plan) {
		StringBuilder sb = new StringBuilder("personId :\t");
		sb.append(plan.getPerson().getId());
		sb.append('\n');
		for (PlanElement pe : plan.getPlanElements()) {
			if (pe instanceof ActivityImpl) {
				ActivityImpl act = (ActivityImpl) pe;
				// if(act.getType())
				sb.append(act.getType());
				sb.append("\tlinkId :\t");
				sb.append(act.getLinkId());
				sb.append("\tcoord :\t");
				sb.append(act.getCoord());
				sb.append('\n');
			} else {
				LegImpl leg = (LegImpl) pe;
				sb.append(leg.getMode());
				sb.append('\n');
			}
		}
		return sb.toString();
	}

	public void close() {
		System.out.println("persons\t" + n);
		System.out.println("false\t" + f);
		System.out.println("nullCounts\t" + nullCnt);
		writer.close();
	}

	public static void main(String[] args) {
		final String netFilename = "../schweiz-ivtch-SVN/baseCase/network/ivtch-osm.xml";
		final String plansFilename = "../matsimTests/changeLegModeTests/500.plans.xml.gz";
		final String outputFilename = "../matsimTests/changeLegModeTests/500.plans.sensibleParkLocation.txt.gz";
		// final String netFilename =
		// "/work/chen/data/ivtch/input/ivtch-osm.xml";
		// final String plansFilename =
		// "/net/ils/chen/tests/changeLegMode/output/ITERS/it.500/500.plans.xml.gz";
		// final String outputFilename =
		// "/net/ils/chen/tests/changeLegMode/output/ITERS/it.500/500.plans.sensibleParkLocation.txt.gz";

		NetworkLayer network = new NetworkLayer();
		new MatsimNetworkReader(network).readFile(netFilename);

		System.out.println("-----> begins \"read population\"");
		Population population = new PopulationImpl();
		new MatsimPopulationReader(population, network).readFile(plansFilename);
		System.out.println("-----> done \"read population\"");

		SensibleParkLocation spl = new SensibleParkLocation(outputFilename);
		System.out.println("-----> \"run population\" begins");
		spl.run(population);
		spl.close();

		System.out.println("--> Done!");
	}
}
