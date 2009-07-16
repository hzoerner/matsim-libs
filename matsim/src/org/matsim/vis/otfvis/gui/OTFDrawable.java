/* *********************************************************************** *
 * project: org.matsim.*
 * OTFDrawable.java
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

package org.matsim.vis.otfvis.gui;

import org.matsim.vis.otfvis.caching.SceneGraph;

/**
 * Any object that can be drawn on screen will have to implement this interface.
 * Normally these classes will additionally implement OTFData.Recevier.
 * 
 * @author dstrippgen
 *
 */
public interface OTFDrawable {
	public void draw();
	public void invalidate(SceneGraph graph);
}
