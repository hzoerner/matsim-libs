/* *********************************************************************** *
 * project: org.matsim.*
 * OTFDataSimpleAgent.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package org.matsim.vis.otfvis.data;

/**
 * OTFDataSimpleAgent transferres the agent data to the visualizer, user and color are free to be defined by the actual Writer class. 
 * @author dstrippgen
 *
 */
public interface OTFDataSimpleAgent extends OTFData{
	
	public static interface Receiver extends OTFData.Receiver{
		public void setAgent(char[] id, float startX, float startY, int state, int user, float color);
	}

}
