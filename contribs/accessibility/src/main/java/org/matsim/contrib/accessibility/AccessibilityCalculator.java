package org.matsim.contrib.accessibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.accessibility.interfaces.FacilityDataExchangeInterface;
import org.matsim.contrib.accessibility.utils.AggregationObject;
import org.matsim.contrib.accessibility.utils.Coord2CoordTimeDistanceTravelDisutility;
import org.matsim.contrib.accessibility.utils.ProgressBar;
import org.matsim.contrib.matrixbasedptrouter.PtMatrix;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacilitiesImpl;
import org.matsim.facilities.ActivityFacility;

/**
 * improvements aug'12<ul>
 * <li> accessibility calculation of unified for cell- and zone-base approach
 * <li> large computing savings due reduction of "least cost path tree" execution:
 *   In a pre-processing step all nearest nodes of measuring points (origins) are determined. 
 *   The "least cost path tree" for measuring points with the same nearest node are now only executed once. 
 *   Only the cost calculations from the measuring point to the network is done individually.
 * </ul><p/>  
 * improvements nov'12<ul>
 * <li> bug fixed aggregatedOpportunities method for compound cost factors like time and distance    
 * </ul><p/>
 * improvements jan'13<ul>
 * <li> added pt for accessibility calculation
 * </ul><p/>
 * 
 * improvements june'13<ul>
 * <li> take "main" (reference to matsim4urbansim) out
 * <li> aggregation of opportunities adjusted to handle facilities
 * <li> zones are taken out
 * <li> replaced [[??]]
 * </ul> 
 * <p/> 
 * Design comments:<ul>
 * <li> yyyy This class is quite brittle, since it does not use a central disutility object, but produces its own.  Should be changed.
 * </ul>
 *     
 * @author thomas, knagel
 *
 */
/*package*/ final class AccessibilityCalculator {

	private static final Logger log = Logger.getLogger(AccessibilityCalculator.class);

	// measuring points (origins) for accessibility calculation
	private ActivityFacilitiesImpl measuringPoints;
	// destinations, opportunities like jobs etc ...
	private AggregationObject[] aggregatedOpportunities;

	private Map<Modes4Accessibility, AccessibilityContributionCalculator> calculators = new HashMap<>();

	private PtMatrix ptMatrix;

	private ArrayList<FacilityDataExchangeInterface> zoneDataExchangeListeners = new ArrayList<>();

	private boolean useRawSum	; //= false;
	private double logitScaleParameter;
	private double inverseOfLogitScaleParameter;

	// counter for warning that capacities are not used so far ... in order not to give the same warning multiple times; dz, apr'14
	private static int cnt = 0 ;
	private Map<String, TravelTime> travelTimes;
	private Map<String, TravelDisutilityFactory> travelDisutilityFactories;

	private Scenario scenario;
	private AccessibilityConfigGroup acg;

	private Coord2CoordTimeDistanceTravelDisutility walkTravelDisutility;

	
	@Inject
	AccessibilityCalculator(Map<String, TravelTime> travelTimes, Map<String, TravelDisutilityFactory> travelDisutilityFactories, Scenario scenario, AccessibilityConfigGroup config) {
		this.travelTimes = travelTimes;
		this.travelDisutilityFactories = travelDisutilityFactories;
		this.scenario = scenario;
		this.acg = config;
	}

	
	public void addFacilityDataExchangeListener(FacilityDataExchangeInterface l){
		this.zoneDataExchangeListeners.add(l);
	}

	
	final void initDefaultContributionCalculators() {
		calculators.put(
				Modes4Accessibility.car,
				new NetworkModeAccessibilityContributionCalculator(
						travelTimes.get(TransportMode.car),
						travelDisutilityFactories.get(TransportMode.car),
						//
						walkTravelDisutility,
						//
						scenario));
		calculators.put(
				Modes4Accessibility.freeSpeed,
				new NetworkModeAccessibilityContributionCalculator(
						new FreeSpeedTravelTime(),
						travelDisutilityFactories.get(TransportMode.car),
						//
						walkTravelDisutility,
						//
						scenario));
		calculators.put(
				Modes4Accessibility.walk,
				new ConstantSpeedAccessibilityContributionCalculator(
						TransportMode.walk,
						scenario));
		calculators.put(
				Modes4Accessibility.bike,
				new ConstantSpeedAccessibilityContributionCalculator(
						TransportMode.bike,
						scenario));
		calculators.put(
				Modes4Accessibility.pt,
				new MatrixBasedPtAccessibilityContributionCalculator(
						ptMatrix,
						scenario.getConfig()));
	}
	
	
	/**
	 * setting parameter for accessibility calculation
	 * @param config TODO
	 */
	final void initAccessibilityParameters(Config config) {

		AccessibilityConfigGroup moduleAPCM = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.GROUP_NAME, AccessibilityConfigGroup.class);

		PlanCalcScoreConfigGroup planCalcScoreConfigGroup = config.planCalcScore();

		useRawSum = moduleAPCM.isUsingRawSumsWithoutLn();
		logitScaleParameter = planCalcScoreConfigGroup.getBrainExpBeta();
		inverseOfLogitScaleParameter = 1 / (logitScaleParameter); // logitScaleParameter = same as brainExpBeta on 2-aug-12. kai
		
		this.walkTravelDisutility = (Coord2CoordTimeDistanceTravelDisutility) travelDisutilityFactories.get(TransportMode.walk).createTravelDisutility(
				travelTimes.get(TransportMode.walk), scenario.getConfig().planCalcScore());
	}

	
	/**
	 * This aggregates the disutilities Vjk to get from node j to all k that are attached to j.
	 * Finally the sum(Vjk) is assigned to node j, which is done in this method.
	 * 
	 *     j---k1 
	 *     |\
	 *     | \
	 *     k2 k3
	 *
	 * Calculates the sum of disutilities Vjk, i.e. the disutilities to reach all opportunities k that are assigned to j from node j
	 *
	 * @param opportunities such as workplaces, either given at a parcel- or zone-level
	 * @param network giving the road network
	 */
	final void aggregateOpportunities(final ActivityFacilities opportunities, Network network){
		// yyyy this method ignores the "capacities" of the facilities. kai, mar'14
		// for now, we decided not to add "capacities" as it is not needed for current projects. dz, feb'16

		log.info("Aggregating " + opportunities.getFacilities().size() + " opportunities with identical nearest node ...");
		Map<Id<Node>, AggregationObject> opportunityClusterMap = new ConcurrentHashMap<>();
		ProgressBar bar = new ProgressBar( opportunities.getFacilities().size() );

		for ( ActivityFacility opportunity : opportunities.getFacilities().values() ) {
			bar.update();

			Node nearestNode = ((NetworkImpl)network).getNearestNode( opportunity.getCoord() );
			
			double walkUtility = -this.walkTravelDisutility.getCoord2CoordTravelDisutility(opportunity.getCoord(), nearestNode.getCoord());
			double expVjk = Math.exp(this.logitScaleParameter * walkUtility);
			
			AggregationObject jco = opportunityClusterMap.get( nearestNode.getId() ) ;
			if ( jco == null ) {
				jco = new AggregationObject(opportunity.getId(), null, null, nearestNode, 0. ); // initialize with zero!
				opportunityClusterMap.put( nearestNode.getId(), jco ) ; 
			}
			if ( cnt == 0 ) {
				cnt++;
				log.warn("ignoring the capacities of the facilities");
				log.warn(Gbl.ONLYONCE);
			}
			jco.addObject( opportunity.getId(), expVjk ) ;
		}
		log.info("Aggregated " + opportunities.getFacilities().size() + " number of opportunities to " + opportunityClusterMap.size() + " nodes.");
		this.aggregatedOpportunities = opportunityClusterMap.values().toArray(new AggregationObject[opportunityClusterMap.size()]);
	}

	
	final void computeAccessibilities(Scenario scenario, Double departureTime) {
		SumOfExpUtils[] gcs = new SumOfExpUtils[Modes4Accessibility.values().length] ;
		// this could just be a double array, or a Map.  Not using a Map for computational speed reasons (untested);
		// not using a simple double array for type safety in long argument lists. kai, feb'14
		for ( int ii=0 ; ii<gcs.length ; ii++ ) {
			gcs[ii] = new SumOfExpUtils() ;
		}

		// Condense measuring points (origins) that have the same nearest node on the network
		Map<Id<Node>, ArrayList<ActivityFacility>> aggregatedOrigins = aggregateMeasurePointsWithSameNearestNode(
				scenario);
		log.info("Now going through all origins:");
		ProgressBar bar = new ProgressBar(aggregatedOrigins.size());
		
		// go through all nodes (keys) that have a measuring point (origin) assigned
		for (Id<Node> nodeId : aggregatedOrigins.keySet()) {
			bar.update();

			Node fromNode = scenario.getNetwork().getNodes().get(nodeId);

			for (AccessibilityContributionCalculator calculator : calculators.values()) {
				calculator.notifyNewOriginNode(fromNode, departureTime);
			}

			// get list with origins that are assigned to "fromNode"
			for ( ActivityFacility origin : aggregatedOrigins.get( nodeId ) ) {
				assert( origin.getCoord() != null );
				
				for (SumOfExpUtils gc : gcs) {
					gc.reset();
				}

				// --------------------------------------------------------------------------------------------------------------
				// goes through all opportunities, e.g. jobs, (nearest network node) and calculate/add their exp(U) contributions:
				for (final AggregationObject aggregatedFacility : this.aggregatedOpportunities) {
					computeAndAddExpUtilContributions( gcs, origin, aggregatedFacility, departureTime );
				}
				// --------------------------------------------------------------------------------------------------------------
				// What does the aggregation of the starting locations save if we do the just ended loop for all starting
				// points separately anyways?  Answer: The trees need to be computed only once.  (But one could save more.) kai, feb'14

				// aggregated value
				Map< Modes4Accessibility, Double> accessibilities  = new HashMap<>() ;

				for ( Modes4Accessibility mode : acg.getIsComputingMode() ) {
					if(!useRawSum){ 	// get log sum
						accessibilities.put( mode, inverseOfLogitScaleParameter * Math.log( gcs[mode.ordinal()].getSum() ) ) ;
					} else {
						// this was used by IVT within SustainCity.  Not sure if we should maintain this; they could, after all, just exp the log results. kai, may'15
						accessibilities.put( mode, gcs[mode.ordinal()].getSum() ) ;
//							accessibilities.put( mode, inverseOfLogitScaleParameter * gcs[mode.ordinal()].getSum() ) ;
						// yyyy why _multiply_ with "inverseOfLogitScaleParameter"??  If anything, would need to take the power:
						// a * ln(b) = ln( b^a ).  kai, jan'14
					}
				}

				for (FacilityDataExchangeInterface zoneDataExchangeInterface : this.zoneDataExchangeListeners) {
					//log.info("here");
					zoneDataExchangeInterface.setFacilityAccessibilities(origin, departureTime, accessibilities);
				}

			}

		}
		for (FacilityDataExchangeInterface zoneDataExchangeInterface : this.zoneDataExchangeListeners) {
			zoneDataExchangeInterface.finish();
		}
	}

	
	/**
	 * This method condenses measuring points (origins) that have the same nearest node on the network
	 * @param scenario
	 * @return
	 */
	private Map<Id<Node>, ArrayList<ActivityFacility>> aggregateMeasurePointsWithSameNearestNode(Scenario scenario) {
		Map<Id<Node>,ArrayList<ActivityFacility>> aggregatedOrigins = new ConcurrentHashMap<>();
		
		for (ActivityFacility measuringPoint : measuringPoints.getFacilities().values()) {

			// determine nearest network node (from- or toNode) based on the link
			Node fromNode = NetworkUtils.getCloserNodeOnLink(measuringPoint.getCoord(),
					((NetworkImpl)scenario.getNetwork()).getNearestLinkExactly(measuringPoint.getCoord()));

			// this is used as a key for hash map lookups
			Id<Node> nodeId = fromNode.getId();

			// create new entry if key does not exist!
			if(!aggregatedOrigins.containsKey(nodeId)) {
				aggregatedOrigins.put(nodeId, new ArrayList<ActivityFacility>());
			}
			// assign measure point (origin) to it's nearest node
			aggregatedOrigins.get(nodeId).add(measuringPoint);
		}
		log.info("Number of measurement points (origins): " + measuringPoints.getFacilities().values().size());
		log.info("Number of aggregated measurement points (origins): " + aggregatedOrigins.size());
		return aggregatedOrigins;
	}

	
	private void computeAndAddExpUtilContributions( SumOfExpUtils[] gcs, ActivityFacility origin, 
			final AggregationObject aggregatedFacility, Double departureTime) {
		for ( Map.Entry<Modes4Accessibility, AccessibilityContributionCalculator> calculatorEntry : calculators.entrySet() ) {
			if ( !this.acg.getIsComputingMode().contains(calculatorEntry.getKey()) ) continue; // XXX should be configured by adding only the relevant calculators
			final double expVhk = calculatorEntry.getValue().computeContributionOfOpportunity( origin , aggregatedFacility, departureTime );
			gcs[ calculatorEntry.getKey().ordinal() ].addExpUtils( expVhk );
		}
	}

	
	public void setComputingAccessibilityForMode( Modes4Accessibility mode, boolean val ) {
		this.acg.setComputingAccessibilityForMode(mode, val);
	}

	
	public Set<Modes4Accessibility> getIsComputingMode() {
		return this.acg.getIsComputingMode();
	}

	
	/**
	 * stores travel disutilities for different modes
	 */
	static class SumOfExpUtils {
		// could just use Map<Modes4Accessibility,Double>, but since it is fairly far inside the loop, I leave it with primitive
		// variables on the (unfounded) intuition that this helps with computational speed.  kai, 

		private double sum  	= 0.;

		void reset() {
			this.sum		  	= 0.;
		}

		void addExpUtils(double val){
			this.sum += val;
		}

		double getSum(){
			return this.sum;
		}
	}

	
	public final void setPtMatrix(PtMatrix ptMatrix) {
		this.ptMatrix = ptMatrix;
	}

	
	ActivityFacilitiesImpl getMeasuringPoints() {
		return measuringPoints;
	}

	
	void setMeasuringPoints(ActivityFacilitiesImpl measuringPoints) {
		this.measuringPoints = measuringPoints;
	}
}