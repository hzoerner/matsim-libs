/**
 *
 */
package playground.yu.newPlans;

import org.matsim.core.api.experimental.population.Population;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.PopulationReader;
import org.matsim.population.algorithms.PlanAlgorithm;

/**
 * @author yu
 * 
 */
public class InitialHomeEndTime extends NewPopulation implements PlanAlgorithm {

	/**
	 * @param population
	 * @param filename
	 */
	public InitialHomeEndTime(final Population population, final String filename) {
		super(population, filename);
	}

	@Override
	public void run(final PersonImpl person) {
		for (PlanImpl pl : person.getPlans())
			run(pl);
		this.pw.writePerson(person);
	}

	public void run(final PlanImpl plan) {
		plan.getFirstActivity().setEndTime(21600.0);
	}

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		final String netFilename = "../schweiz-ivtch-SVN/baseCase/network/ivtch-osm.xml";
		final String plansFilename = "../schweiz-ivtch-SVN/baseCase/plans/plans_all_zrh30km_transitincl_10pct.xml.gz";
		final String outputPlansFilename = "output/plans_all_zrh30km_transitincl_10pct_home_end_6h.xml.gz";

		NetworkLayer network = new NetworkLayer();
		new MatsimNetworkReader(network).readFile(netFilename);

		Population population = new PopulationImpl();
		InitialHomeEndTime ihet = new InitialHomeEndTime(population,
				outputPlansFilename);

		PopulationReader plansReader = new MatsimPopulationReader(population,
				network);
		plansReader.readFile(plansFilename);

		ihet.run(population);

		ihet.writeEndPlans();
	}

}
