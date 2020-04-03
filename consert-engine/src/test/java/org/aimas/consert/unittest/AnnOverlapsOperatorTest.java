package org.aimas.consert.unittest;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.aimas.consert.engine.core.EventTracker;
import org.aimas.consert.model.annotations.DefaultAnnotationData;
import org.aimas.consert.model.operators.AnnOverlapsOperator.AnnOverlapsEvaluatorDefinition;
import org.aimas.consert.tests.hla.assertions.Position;
import org.aimas.consert.tests.hla.assertions.SittingLLA;
import org.aimas.consert.tests.hla.entities.Area;
import org.aimas.consert.tests.hla.entities.Person;
import org.aimas.consert.utils.TestSetup;
import org.junit.Assert;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.conf.EvaluatorOption;

public class AnnOverlapsOperatorTest extends TestSetup {
	
    @Test
    public void testNotAnnOverlapsOperator() {
    	// setup KnowledgeBuilderConfiguration to add operator
		KnowledgeBuilderConfiguration builderConf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration();
        builderConf.setOption(EvaluatorOption.get("annOverlaps", new AnnOverlapsEvaluatorDefinition()));
        
		// setup KieSession
		KieSession kSession = getKieSessionFromResources(builderConf, null, "operator_test_rules/overlapsTest.drl" );
		EventTracker eventTracker = new EventTracker(kSession);
		TrackingAgendaEventListener agendaEvListener = new TrackingAgendaEventListener();
		kSession.addEventListener(agendaEvListener);
		
		// create a Person
		Person person = new Person("Alex");
		
		// create a Position ContextAssertion
		long posStart = kSession.getSessionClock().getCurrentTime() - 5000;
		long posEnd = posStart + 5000;
		
		Position pos = new Position(person, new Area("WORK_AREA"), 
				new DefaultAnnotationData(posEnd, 1.0, new Date(posStart), new Date(posEnd)));
		
		// create a SittingLLA ContextAssertion
		long llaStart = posStart - 5000;
		long llaEnd = posEnd - 5000;
		
		SittingLLA lla = new SittingLLA(person, new DefaultAnnotationData(llaEnd, 1.0, new Date(llaStart), new Date(llaEnd)));
		
		// insert both assertions in session
		eventTracker.insertDerivedEvent(pos);
		eventTracker.insertDerivedEvent(lla);
		
		// fire all rules
		int nrFired = kSession.fireAllRules();
		
		List<Match> activations = agendaEvListener.getMatchList();
		System.out.println(activations);
		
		// there should be 2 rules, the one that triggers the HLA insertion and the one that prints the HLA
		Assert.assertEquals("Number of rules fired different than expected.", 1, nrFired);
		
		// see if derived WorkingHLA exists in internal memory 
		Collection<FactHandle> hlaFacts = kSession.getEntryPoint("ExtendedWorkingHLAStream").getFactHandles();
		
		Assert.assertEquals("Number of derived HLA facts different than expected", 0, hlaFacts.size()); 
    }
}
