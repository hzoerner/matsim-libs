/* *********************************************************************** *
 * project: org.matsim.*
 * TolledTripLengthCalculator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package playground.anhorni.surprice.analysis;

import java.util.TreeMap;

import org.matsim.analysis.Bins;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.AgentArrivalEvent;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.handler.AgentArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.roadpricing.RoadPricingScheme;
import org.matsim.roadpricing.RoadPricingSchemeImpl.Cost;
import org.matsim.utils.objectattributes.ObjectAttributes;

/**
 * Calculates the distance of a trip which occurred on tolled links.
 * Requires roadpricing to be on.
 */
public class TolledTripLengthCalculator implements LinkEnterEventHandler, AgentArrivalEventHandler {
	private double sumLength = 0.0;
	private int cntTrips = 0;
	private RoadPricingScheme scheme = null;
	private Network network = null;
	private TreeMap<Id, Double> agentDistance = null;	
	private static Double zero = Double.valueOf(0.0);
	
	private Bins tolltdBins;
	private ObjectAttributes incomes;

	public TolledTripLengthCalculator(final Network network, final RoadPricingScheme scheme, Bins tolltdBins, ObjectAttributes incomes) {
		this.scheme = scheme;
		this.network = network;
		this.agentDistance = new TreeMap<Id, Double>();
		this.tolltdBins = tolltdBins;
		this.incomes = incomes;
	}

	@Override
	public void handleEvent(final LinkEnterEvent event) {
		
		// getting the (monetary? generalized?) cost of the link
		Cost cost = this.scheme.getLinkCostInfo(event.getLinkId(), event.getTime(), event.getPersonId() );
		
		if (cost != null) {
			// i.e. if there is a toll on the link
			
			Link link = this.network.getLinks().get(event.getLinkId());
			if (link != null) {
				
				// get some distance that has been accumulated (how?) up to this point:
				Double length = this.agentDistance.get(event.getPersonId());
				
				// if nothing has been accumlated so far, initialize this at zero:
				if (length == null) {
					length = zero;
				}
				
				// add the new length to the already accumulated length:
				length = Double.valueOf(length.doubleValue() + link.getLength());
				
				// put the result again in the "memory":
				this.agentDistance.put(event.getPersonId(), length);
			}
		}
	}

	@Override
	public void handleEvent(final AgentArrivalEvent event) {
		// at arrival of the agent ...
		
		// get the accumulated "tolled" length from the agent
		Double length = this.agentDistance.get(event.getPersonId());
		if (length != null) {
			// if this is not zero, accumulate it into some global accumulated length ...
			this.sumLength += length.doubleValue();
			
			double income = Double.parseDouble((String)this.incomes.getAttribute(event.getPersonId().toString(), "income"));
			this.tolltdBins.addVal(income, length.doubleValue());
			
			// ... and reset the agent-individual accumlated length to zero:
			this.agentDistance.put(event.getPersonId(), zero);
			this.cntTrips++;
		}

		// count _all_ finished trips (independent off toll payment):
//		this.cntTrips++;
	}

	@Override
	public void reset(final int iteration) {
		this.sumLength = 0.0;
		this.cntTrips = 0;
	}

	public double getAverageTripLength() {
		if (this.cntTrips == 0) return 0;
//		log.warn("NOTE: The result of this calculation has been changed from 'av over all trips' to 'av over tolled trips'.  kai/benjamin, apr'10") ;
		// commenting this out.  kai, mar'12
		return (this.sumLength / this.cntTrips);
	}
}
