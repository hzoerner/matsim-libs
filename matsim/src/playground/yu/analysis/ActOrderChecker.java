/**
 *
 */
package playground.yu.analysis;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.experimental.population.PlanElement;
import org.matsim.core.api.experimental.population.Population;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.population.algorithms.AbstractPersonAlgorithm;
import org.matsim.population.algorithms.PlanAlgorithm;

import playground.yu.utils.io.SimpleWriter;

/**
 * @author yu
 * 
 */
public class ActOrderChecker extends AbstractPersonAlgorithm implements
		PlanAlgorithm {
	// ------------------------------------------------------------------------
	public static class ActOder {
		public static String getActOder(final PlanImpl plan) {
			StringBuffer acts = new StringBuffer();
			for (PlanElement pe : plan.getPlanElements()) {
				if (pe instanceof ActivityImpl) {
					acts.append(((ActivityImpl) pe).getType());
				}
			}
			return acts.toString();
		}
	}

	// --------------------------------------------------------------------------
	private Id personId;
	private final Map<Id, String> actsMap = new HashMap<Id, String>();

	public Map<Id, String> getActsMap() {
		return this.actsMap;
	}

	@Override
	public void run(final PersonImpl person) {
		this.personId = person.getId();
		run(person.getSelectedPlan());
	}

	public void run(final PlanImpl plan) {
		this.actsMap.put(this.personId, ActOder.getActOder(plan));
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		Gbl.startMeasurement();

		final String netFilename = args[0];
		final String plansFilenameA = args[1];
		final String plansFilenameB = args[2];
		final String outputFilename = args[3];

//		Gbl.createConfig(null);

		NetworkLayer network = new NetworkLayer();
		new MatsimNetworkReader(network).readFile(netFilename);

		Population populationA = new PopulationImpl();
		ActOrderChecker aocA = new ActOrderChecker();
		new MatsimPopulationReader(populationA, network)
				.readFile(plansFilenameA);
		aocA.run(populationA);

		Population populationB = new PopulationImpl();
		ActOrderChecker aocB = new ActOrderChecker();
		new MatsimPopulationReader(populationB, network)
				.readFile(plansFilenameB);
		aocB.run(populationB);

		SimpleWriter writer = new SimpleWriter(outputFilename);
		if (writer != null) {
			writer.writeln("personId\toriginal ActChain\tcurrent ActChain");
			Map<Id, String> actsA = aocA.getActsMap();
			Map<Id, String> actsB = aocB.getActsMap();
			int c = 0, changed = 0;
			for (Entry<Id, String> personEntry : actsA.entrySet()) {
				c++;
				String personId = personEntry.getKey().toString();
				String actChainA = personEntry.getValue();
				String actChainB = actsB.get(personEntry);
				if (!actChainA.equals(actChainB)) {
					writer.writeln(personId + "\t" + actChainA + "\t"
							+ actChainB);
					changed++;
				}
				writer.writeln(personId + "\t" + actChainA + "\t" + actChainB);
			}
			writer.writeln("agents :\t" + c + "\tchanged :\t" + changed);
			writer.close();
		}

		System.out.println("--> Done!");
		Gbl.printElapsedTime();
		System.exit(0);
	}
}
