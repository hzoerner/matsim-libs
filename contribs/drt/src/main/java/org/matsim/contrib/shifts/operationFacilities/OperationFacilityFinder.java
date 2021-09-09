package org.matsim.contrib.shifts.operationFacilities;

import org.matsim.api.core.v01.Coord;

/**
 * @author nkuehnel
 */
public interface OperationFacilityFinder {

    OperationFacility findFacilityOfType(Coord coord, OperationFacilityType type);

    OperationFacility findFacility(Coord coord);
}
