package org.aimas.consert.unittest;


import java.util.ArrayList;
import java.util.List;

import org.aimas.consert.engine.EngineRunner;
import org.aimas.consert.engine.EventTracker;
import org.aimas.consert.tests.hla.TestSetup;
import org.kie.api.runtime.KieSession;

/**
 * Created by alex on 06.04.2017.
 */
public class AccumulateTestRunner extends TestSetup {
	
    public static void main(String[] args) {
    	
    	try {
	    	// load up the knowledge base
//	        KieServices ks = KieServices.Factory.get();
//	        KieSessionConfiguration config = ks.newKieSessionConfiguration();
//	        config.setOption(ClockTypeOption.get("realtime"));
//	        
//		    KieContainer kContainer = ks.getKieClasspathContainer();
//	    	KieSession kSession = kContainer.newKieSession("ksession-rules", config);

    		KieSession kSession = getKieSessionFromResources( "accumulate_test_rules/accumulateTest.drl" );
            final List<Long> results = new ArrayList<Long>();

            kSession.setGlobal( "results", results );
    		
	    	// set up engine runner thread and event inserter
	    	Thread engineRunner = new Thread(new EngineRunner(kSession));
	    	
	    	EventTracker eventTracker = new EventTracker(kSession);
	    	TestInserter eventInserter = new TestInserter(eventTracker);
	    	
	    	// start the engine thread and the inserter, wait for the inserter to finish then exit
	    	engineRunner.start();
	    	eventInserter.start();
	    	
	    	while (!eventInserter.isFinished()) {
	    		Thread.sleep(2000);
	    	}
	    	
	    	eventInserter.stop();
	    	
	    	// verify if results only contain values <= 1 
	    	System.out.println(results);
	    	for (Long l : results) {
	    		if (l > 1) {
	    			System.out.println("IT DOES NOT WORK! GOT A COUNT GREATER THAN 1.");
	    			break;
	    		}
	    	}
	    	System.out.println("WE HAVE A WINNER. ALL COUNTS WITHIN WINDOW ARE <=1.");
	    	
	    	
	    	kSession.halt();
	    	kSession.dispose();
	    	
	    	engineRunner.join(1000);
    	}
    	catch(Exception ex) {
    		ex.printStackTrace();
    	}
    	
    	
    	/*
    	HLATest test = new HLATest();

        File inputFile = getFileNameFromResources("files/single_hla_120s_01er_015fd.json");
        Queue<Object> events = JSONEventReader.parseEvents(inputFile);

        System.out.println(events);
        */
    }
    
    
}
