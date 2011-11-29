/* *********************************************************************** *
 * project: org.matsim.*
 * Plansgenerator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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
package playground.droeder.eMobility;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.ActivityEndEvent;
import org.matsim.core.api.experimental.events.ActivityStartEvent;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.api.experimental.events.PersonEvent;
import org.matsim.core.api.experimental.events.handler.ActivityEndEventHandler;
import org.matsim.core.api.experimental.events.handler.ActivityStartEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkLeaveEventHandler;

/**
 * @author droeder
 *
 */
public class ElectroVehicleHandler implements LinkEnterEventHandler, LinkLeaveEventHandler, 
												ActivityStartEventHandler, ActivityEndEventHandler{

	private Map<Id, ElectroVehicle> vehicles;
	private HashMap<Id, LinkEnterEvent> disCharging;
	private HashMap<Id, ActivityStartEvent> charging;
	private ChargingProfiles chargingProfiles;
	private Network net;

	public ElectroVehicleHandler(Map<Id, ElectroVehicle> vehicles, ChargingProfiles profiles, Network net){
		this.vehicles = vehicles;
		this.disCharging = new HashMap<Id, LinkEnterEvent>();
		this.charging = new HashMap<Id, ActivityStartEvent>();
		this.chargingProfiles = profiles;
		this.net = net;
	}

	@Override
	public void reset(int iteration) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		this.processEvent(event);
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		this.processEvent(event);
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		this.processEvent(event);
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		this.processEvent(event);
	}
	
	private void processEvent(PersonEvent e){
		//TODO change to vehId
		if(!this.vehicles.containsKey(e.getPersonId())){
			// no eletroVehicle
			return;
		}else{
			ElectroVehicle v = this.vehicles.get(e.getPersonId());
			if(e instanceof ActivityEndEvent){
				if(this.charging.containsKey(v.getId())){
					// it is not the first activity, so charge the vehicle depending on the given data
					double charge = this.chargingProfiles.getCharge(v.getChargingType(), 
							e.getTime() - this.charging.remove(v.getId()).getTime(), 
							v.getChargeState());
					v.charge(charge, e.getTime());
				}else{
					/* it is the first activity of an agent, we don't know anything about the duration of it's activity,
					 * so we can not charge here
					 */
				}
			}else if(e instanceof LinkLeaveEvent){
				if(this.disCharging.containsKey(v.getId())){
					v.disCharge(this.calcDischarge(v, this.disCharging.remove(v.getId()), e), e.getTime());
				}else{
					/*  this must be a LinkLeaveEvent after an activity. 
					 *  The activity is located at the end of the link, so the discharging should happen with the ActStartEvent
					 */
				}
			}else if(e instanceof LinkEnterEvent){
				//store the event to process it later
				this.disCharging.put(v.getId(), (LinkEnterEvent) e);
			}else if(e instanceof ActivityStartEvent){
				// store the event to process it later
				this.charging.put(v.getId(), (ActivityStartEvent) e);
				/* discharge here for the last passed link otherwise the avSpeedCalculation will be wrong,
				 * because the TT then includes the activityDuration 
				 * we don't need to check if a LinkEnterEvent is stored, because it must be stored at this time
				 */
				v.disCharge(this.calcDischarge(v, this.disCharging.remove(v.getId()), e), e.getTime());
			}
		}
	}
	
	private Double calcDischarge(ElectroVehicle v, LinkEnterEvent enter, PersonEvent e) {
		Link l = this.net.getLinks().get(enter.getLinkId());
		double avSpeed = calculateAvSpeed(enter.getTime(), e.getTime(), l);
		double gradient = calculateGradient(l) ;
		return this.chargingProfiles.getCharge(v.getDisChargingType(), avSpeed, gradient);
	}

	private double calculateAvSpeed(double start, double end, Link l){
		//TODO implement
		return 1.0;
	}
	
	private double calculateGradient(Link l){
		//TODO implement
		return 1.0;
	}
	
}
