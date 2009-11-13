package org.matsim.evacuation.run;

import org.matsim.core.utils.misc.CRCChecksum;
import org.matsim.testcases.MatsimTestCase;

public class ShelterEvacuationControllerTest extends MatsimTestCase{
	
	public void testShelterEvacuationController() {
		String config = getInputDirectory() + "config.xml";
		String refEventsFile = getInputDirectory() + "events.txt.gz";
		String testEventsFile = getOutputDirectory() +"ITERS/it.10/10.events.txt.gz";
		
		
		EvacuationQSimControllerII controler = new EvacuationQSimControllerII(new String [] {config});
		controler.setCreateGraphs(false);
		controler.run();
		assertEquals("different events-files.", CRCChecksum.getCRCFromFile(refEventsFile),	CRCChecksum.getCRCFromFile(testEventsFile));
		
		
	}

}
