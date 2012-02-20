/* *********************************************************************** *
 * project: org.matsim.*
 * AgentsTracker.java
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

package playground.christoph.evacuation.mobsim;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.ActivityEndEvent;
import org.matsim.core.api.experimental.events.ActivityStartEvent;
import org.matsim.core.api.experimental.events.AgentArrivalEvent;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.api.experimental.events.handler.ActivityEndEventHandler;
import org.matsim.core.api.experimental.events.handler.ActivityStartEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentDepartureEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkLeaveEventHandler;
import org.matsim.core.events.PersonEntersVehicleEvent;
import org.matsim.core.events.PersonLeavesVehicleEvent;
import org.matsim.core.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.core.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.PlanAgent;
import org.matsim.core.mobsim.framework.events.SimulationInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.SimulationInitializedListener;
import org.matsim.ptproject.qsim.QSim;

import playground.christoph.evacuation.mobsim.Tracker.Position;

public class AgentsTracker implements AgentDepartureEventHandler, AgentArrivalEventHandler,
		ActivityStartEventHandler, ActivityEndEventHandler, 
		PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, 
		LinkEnterEventHandler, LinkLeaveEventHandler, SimulationInitializedListener {

	private final Map<Id, AgentPosition> agentPositions;
	
	public AgentsTracker() {
		this.agentPositions = new HashMap<Id, AgentPosition>();
	}
	
	public AgentPosition getAgentPosition(Id agentId) {
		return agentPositions.get(agentId);
	}
	
	@Override
	public void handleEvent(LinkEnterEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.entersLink(event.getLinkId());
	}
	
	@Override
	public void handleEvent(LinkLeaveEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.leavesLink();
	}
	
	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.entersVehicle(event.getVehicleId());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.leavesVehicle();
	}

	@Override
	public void handleEvent(AgentArrivalEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.setTransportMode(null);
	}

	@Override
	public void handleEvent(AgentDepartureEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.entersLink(event.getLinkId());
		agentPosition.setTransportMode(event.getLegMode());
	}
	
	@Override
	public void handleEvent(ActivityStartEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.entersFacility(event.getFacilityId());
	}
	
	@Override
	public void handleEvent(ActivityEndEvent event) {
		AgentPosition agentPosition = agentPositions.get(event.getPersonId());
		agentPosition.leavesFacility();
	}

	@Override
	public void reset(int iteration) {
		this.agentPositions.clear();
	}

	@Override
	public void notifySimulationInitialized(SimulationInitializedEvent e) {
		this.agentPositions.clear();
		
		QSim sim = (QSim) e.getQueueSimulation();
		for (MobsimAgent agent : sim.getAgents()) {
			PlanElement planElement = ((PlanAgent) agent).getCurrentPlanElement();
			Id facilityId = ((Activity) planElement).getFacilityId();
			AgentPosition agentPosition = new AgentPosition(agent, facilityId, Position.FACILITY);
			this.agentPositions.put(agent.getId(), agentPosition);
		}
	}
	
}
