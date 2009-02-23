package playground.anhorni.locationchoice.cs.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.gbl.Gbl;
import org.matsim.interfaces.basic.v01.Id;
import org.matsim.utils.io.IOUtils;
import playground.anhorni.locationchoice.cs.helper.ChoiceSet;
import playground.anhorni.locationchoice.cs.helper.ZHFacility;

public class ChoiceSetWriterSimple extends CSWriter {

	private final static Logger log = Logger.getLogger(ChoiceSetWriterSimple.class);
	private String mode;
	private String crowFly;
	private TreeMap<Id, ArrayList<ZHFacility>> zhFacilitiesByLink = new TreeMap<Id, ArrayList<ZHFacility>>();
	
	public ChoiceSetWriterSimple(String mode, String crowFly, TreeMap<Id, ArrayList<ZHFacility>> zhFacilitiesByLink) {
		this.mode = mode;
		this.crowFly = crowFly;
		this.zhFacilitiesByLink = zhFacilitiesByLink;
	}
	
	public void write(String outdir, String name, List<ChoiceSet> choiceSets)  {
		
		this.writeNumberOfAlternatives(outdir, name, choiceSets);
	
		String outfile = outdir + name + "_ChoiceSets.txt";	
		if (!super.checkBeforeWriting(choiceSets)) {
			log.warn(outfile +" not created");
			return;
		}
		
		String header="Id\t" +
		"WP\tChoice\tAge\tGender\tIncome\tNumber_of_personsHH\tCivil_Status\tEducation\tTime_of_purchase\tstart_is_home\tTTB" ;

		for (int i = 0; i < this.zhFacilitiesByLink.size(); i++) {
			header += "SH" + i + "_Shop_id\t +" +
					"SH" + i + "_AV" +
					"SH" + i + "_Mapped_x\t" + "SH" + i + "_Mapped_y\t" +
					"SH" + i + "_Exact_x\t" + "SH" + i + "_Exact_y\t +" +
					"SH" + i + "_Travel_time_in_Net\t" + 
					"SH" + i + "_Travel_distance_in_net\t +" +
					"SH" + i + "_Crow_fly_distance_exact\t" + "SH" + i + "_Crow_fly_distance_mapped\t" +
					"SH" + i + "RetailerID" +
					"SH" + i + "Size" +
					"SH" + i + "dHalt" +
					"SH" + i + "aAlt02" +
					"SH" + i + "aAlt10" +
					"SH" + i + "aAlt20" +
					"SH" + i + "HRS_WEEK";
		}
	
		try {								
			final BufferedWriter out = IOUtils.getBufferedWriter(outfile);
			out.write(header);
			out.newLine();		
			
			Iterator<ChoiceSet> choiceSet_it = choiceSets.iterator();
			while (choiceSet_it.hasNext()) {
				ChoiceSet choiceSet = choiceSet_it.next();

				String outLine = "" + choiceSet.getPersonAttributes().getWP() +"\t";
				
				// chosen facility is always 1st alternative in choice set
				String choice = "1\t";
				outLine += choice;
				
				outLine += choiceSet.getPersonAttributes().getAge() +"\t"+ choiceSet.getPersonAttributes().getGender() +"\t"+ 
				choiceSet.getPersonAttributes().getIncomeHH() +"\t"+ choiceSet.getPersonAttributes().getNumberOfPersonsHH() +"\t";
				
				// chosen facility:
				outLine += choiceSet.getChosenZHFacility().getId() +"\t" + "1\t";
								
				Iterator<ArrayList<ZHFacility>> facilities_it = zhFacilitiesByLink.values().iterator();
				while (facilities_it.hasNext()) {
					ArrayList<ZHFacility> facilitiesList = facilities_it.next();
					
					Iterator<ZHFacility> facilitiesList_it = facilitiesList.iterator();			
					while (facilitiesList_it.hasNext()) {
						ZHFacility facility = facilitiesList_it.next();	

						//AV
						if (!(facility.getId().compareTo(choiceSet.getChosenZHFacility().getId()) == 0)) {
							outLine += facility.getId() +"\t";
							
							if (choiceSet.zhFacilityIsInChoiceSet(facility)) {
								outLine += "1\t";
							}
							else {
								outLine += "0\t";
							}
						}
						
						outLine += 
							facility.getMappedPosition().getX() + "\t" + 
							facility.getMappedPosition().getY()	+ "\t" + 
							facility.getExactPosition().getX() 	+ "\t" +
							facility.getExactPosition().getY();
						
						double crowFlyDistanceMapped = choiceSet.calculateCrowFlyDistanceMapped(facility.getMappedPosition());
						double crowFlyDistanceExact = choiceSet.calculateCrowFlyDistanceExact(facility.getExactPosition());
						
						outLine += choiceSet.getTravelTime(facility) + "\t" +
							choiceSet.getTravelDistance(facility) +"\t" +
							crowFlyDistanceExact +"\t" +
							crowFlyDistanceMapped +"\t";
						
						outLine += facility.getRetailerID() + "\t" +
							facility.getSize_descr() +"\t" +
							facility.getDHalt() + "\t";							
					}
				}
				out.write(outLine);
				out.newLine();
				out.flush();
			}
			out.flush();			
			out.flush();
			out.close();
						
		} catch (final IOException e) {
				Gbl.errorMsg(e);
		}	
	}
	
	
	private void writeNumberOfAlternatives(String outdir, String name,List<ChoiceSet> choiceSets)  {
		
		String outfile_alternatives = outdir + name + "_NumberOfAlternativesInclusive.txt";
		
		try {		
			final BufferedWriter out_alternatives = IOUtils.getBufferedWriter(outfile_alternatives);
			out_alternatives.write("Id\tNumber of alternatives (includes the chosen facility)");
			out_alternatives.newLine();			
			
			Iterator<ChoiceSet> choiceSet_it = choiceSets.iterator();
			while (choiceSet_it.hasNext()) {
				ChoiceSet choiceSet = choiceSet_it.next();
				out_alternatives.write(choiceSet.getId() + "\t" + choiceSet.getFacilities().size());
				out_alternatives.newLine();
				out_alternatives.flush();
			}
			out_alternatives.close();
						
		} catch (final IOException e) {
				Gbl.errorMsg(e);
		}	
	}	
}
