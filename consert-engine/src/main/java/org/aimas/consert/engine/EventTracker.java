package org.aimas.consert.engine;

import org.aimas.consert.engine.ContinuityChecker.ContinuityResult;
import org.aimas.consert.engine.constraint.ConstraintChecker;
import org.aimas.consert.engine.constraint.ConstraintChecker.ConstraintResult;
import org.aimas.consert.engine.TrackedAssertionStore.TrackedAssertionData;
import org.aimas.consert.engine.api.ContextAssertionListener;
import org.aimas.consert.engine.api.ContextAssertionNotifier;
import org.aimas.consert.engine.api.EntityDescriptionListener;
import org.aimas.consert.engine.api.EntityDescriptionNotifier;
import org.aimas.consert.engine.constraint.ConstraintResolutionService;
import org.aimas.consert.engine.constraint.DefaultConstraintResolutionService;
import org.aimas.consert.engine.constraint.UniquenessConflictDecision;
import org.aimas.consert.model.annotations.AnnotationDataFactory;
import org.aimas.consert.model.annotations.DefaultAnnotationData;
import org.aimas.consert.model.annotations.DefaultAnnotationDataFactory;
import org.aimas.consert.model.constraint.IUniquenessConstraintViolation;
import org.aimas.consert.model.content.ContextAssertion;
import org.aimas.consert.model.content.EntityDescription;
import org.drools.core.common.EventFactHandle;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;

import java.util.Date;


public class EventTracker extends BaseEventTracker {

    public static final String CONSTRAINT_STORE = "ConstraintStore";


	
	private AnnotationDataFactory annotationFactory = new DefaultAnnotationDataFactory();
	
	private ContextAssertionNotifier eventNotifier = ContextAssertionNotifier.getNewInstance();
	private EntityDescriptionNotifier factNotifier = EntityDescriptionNotifier.getNewInstance();
	
	public AnnotationDataFactory getAnnotationFactory() {
		return annotationFactory;
	}
	
	public void setAnnotationFactory(AnnotationDataFactory annotationFactory) {
		this.annotationFactory = annotationFactory;
	}

	private ContinuityChecker continuityChecker;
    private ConstraintChecker constraintChecker;

    private ConstraintResolutionService constraintResolutionService;

	public EventTracker(KieSession kSession) {
		super(kSession);
		kSession.setGlobal("eventTracker", this);

		continuityChecker = new ContinuityChecker(this, trackedAssertionStore);
		constraintChecker = new ConstraintChecker(kSession);
		constraintResolutionService = new DefaultConstraintResolutionService();
	}


	/**
	 * Insert an EntityDescription (a fact)
	 * @param fact EntityDescription to be inserted.
	 */
	@Override
	public void insertStaticEvent(EntityDescription fact) {
		kSession.insert(fact);
	}
	
	/**
	 * Remove an EntityDescription. The implementation makes use of the 
	 * <code>hashCode</code> and <code>equals</code> methods of an EntityDescription to identify the 
	 * fact to be deleted in the Working Memory.
	 * @param fact The EntityDescription to be removed.
	 */
	@Override
	public void deleteStaticEvent(EntityDescription fact) {
		FactHandle fh = kSession.getFactHandle(fact);
		
		if (fh != null) {
			kSession.delete(fh);
		}
	}
	
	/**
	 * Insert an event that is not required to go through the verifications OF temporal continuity.
	 * @param event The event to be inserted.
	 * @param setTimestamp Boolean value controlling whether to set the timestamp of the event based on the kSession clock.
	 */
	@Override
	public void insertSimpleEvent(ContextAssertion event, boolean setTimestamp) {
		String eventStream = event.getStreamName();
		
		if (event.getAnnotations().allowsAnnotationInsertion()) {
			if (setTimestamp) {
				DefaultAnnotationData ann = (DefaultAnnotationData)event.getAnnotations();
				ann.setTimestamp(getCurrentTime());
				ann.setStartTime(new Date(getCurrentTime()));
				ann.setEndTime(new Date(getCurrentTime()));
				event.setAnnotations(ann);
			}
			
			/* shouldn't be null but it is*/
//			if (kSession.getEntryPoint(eventStream)!=null)
				kSession.getEntryPoint(eventStream).insert(event);
		}
	}
	
	/**
	 * Remove a ContextAssertion. The implementation makes use of the 
	 * <code>hashCode</code> and <code>equals</code> methods of a ContextAssertion to identify the 
	 * event to be deleted in the Working Memory.
	 * @param event The ContextAssertion to be removed.
	 */
	@Override
	public void deleteEvent(ContextAssertion event) {
		String eventStream = event.getStreamName();
		FactHandle handle = kSession.getEntryPoint(eventStream).getFactHandle(event);
		
		kSession.getEntryPoint(eventStream).delete(handle);
	}

	/**
	 * Insert an event. The event will go through the verifications of temporal continuity.
	 * @param newAssertion The event to insert
	 */
	@Override
    public void insertEvent(final ContextAssertion newAssertion) {
    	String eventStream = newAssertion.getStreamName();
    	
    	// if this is the first event of its type
    	if (!trackedAssertionStore.tracksAssertion(newAssertion.getClass())) {
    		if (newAssertion.getAnnotations().allowsAnnotationInsertion()) {
    			// go through with insertion in the map and the KieBase
    			FactHandle handle = kSession.getEntryPoint(eventStream).insert(newAssertion);
                // TODO - perform constraint check
                trackedAssertionStore.trackAssertion(newAssertion, handle, kSession.getEntryPoint(eventStream));
    		}
    	}
    	else {
    		// afterwards, do the all continuity verification steps as an atomic action
    		kSession.submit(new KieSession.AtomicAction() {
				@Override
				public void execute(KieSession kSession) {
					// check to see if it matches one of the previous stored events by content
                    ContinuityResult continuityResult = continuityChecker.check(newAssertion);

                    if (continuityResult.hasExtension()) {
                        TrackedAssertionData existingAssertionData = continuityResult.getTrackedAssertionData(kSession);
                        EntryPoint existingAssertionEntry = existingAssertionData.getExistingEventEntryPoint();

                        // insert the extended one
                        FactHandle extendedAssertionHandle = kSession.getEntryPoint(continuityResult.getExtendedEventStream())
                                .insert(continuityResult.getExtendedAssertion());

                        ConstraintResult constraintResult =
								constraintChecker.check(continuityResult.getExtendedAssertion());

                        if (!constraintResult.isClear()) {
                            System.out.println("[CONSTRAINT CHECKER] DETECTED CONSTRAINT VIOLATIONS FOR: "
                                    + continuityResult.getExtendedAssertion() + ". Violations:\n" + constraintResult);

                            resolveConflict(constraintResult, continuityResult, extendedAssertionHandle);
                        }
                        else {
                            // No constraints found, so update update kSession and trackedAssertionStore,
                            // remove existing ContextAssertion
                            existingAssertionEntry.delete(continuityResult.getExistingAssertionHandle());
                            trackedAssertionStore.updateTrackedAssertion(existingAssertionData,
                                    continuityResult.getExtendedAssertion(), extendedAssertionHandle);
                        }
                    }
                    else {
                        if (newAssertion.getAnnotations().allowsAnnotationInsertion()) {
                            if (continuityResult.getExistingAssertion() != null) {
                                // If NO annotation continuity, but a previously tracked assertion of the
                                // same content exists, then the newly derived event is allowed for insertion
                                // (from an annotation perspective).
                                // Therefore, it has to make it to the KnowledgeBase => we perform the insert here
                                FactHandle newAssertionHandle = kSession.getEntryPoint(eventStream).insert(newAssertion);

                                // next, run it through the constraint check
                                ConstraintResult constraintResult =
                                        constraintChecker.check(newAssertion);

                                if (!constraintResult.isClear()) {
                                    System.out.println("[CONSTRAINT CHECKER] DETECTED CONSTRAINT VIOLATIONS FOR: "
                                            + newAssertion + ". Violations:\n" + constraintResult);

                                    resolveConflict(constraintResult, newAssertion, newAssertionHandle);
                                } else {
                                    // if there are no violated constraints then it means it
                                    // can also replace the existing event in the lastValidMap
                                    trackedAssertionStore.updateTrackedAssertion(continuityResult.getTrackedAssertionData(kSession),
                                            newAssertion, newAssertionHandle);
                                }
                            }
                            else {
                                System.out.println("CONTENT MISMATCH - NO TRACKED DATA FOR non-extended new Assertion: " + newAssertion);
                                // If allowed for insertion from an annotation perspective, but it DOES NOT match any
                                // monitored event by content, add it to the list of monitored events for this type

                                FactHandle newEventHandle = kSession.getEntryPoint(eventStream).insert(newAssertion);
                                // TODO - perform constraint check
                                trackedAssertionStore.trackAssertion(newAssertion, newEventHandle, kSession.getEntryPoint(eventStream));
                            }
                        }
                    }
				}
			});
    	}
    }


//	/**
//	 * Insert a derived event. This method will usually be called in the right-hand-side of a 
//     * ContextDerivationRule. The method will check for duplicates of events that have already been
//     * derived, but which have still not been garbage collected.
//     * However, no continuity check will be performed
//	 * @param derivedEvent The derived event to be inserted.
//	 */
//	public void insertSimpleDerivedEvent(ContextAssertion derivedEvent) {
//		if (!checkPreviouslyDerived(derivedEvent)) {
//			String derivedEventStream = derivedEvent.getExtendedStreamName();
//			kSession.getEntryPoint(derivedEventStream).insert(derivedEvent);
//		}
//	}
	
    
    /**
     * Insert a derived event. This method will usually be called in the right-hand-side of a 
     * ContextDerivationRule. The method will check for duplicates of events that have already been
     * derived, but which have still not been garbage collected.
     * @param event The event to be inserted
     */
	@Override
    public void insertDerivedEvent(ContextAssertion event) {
    	// Check if event to be inserted exists already
    	if (!checkPreviouslyDerived(event)) {
    		//doDerivedInsertion(event);
            insertEvent(event);
    	}
    	
    }
    
    
	private boolean checkPreviouslyDerived(ContextAssertion derivedEvent) {
		String derivedStreamName = derivedEvent.getStreamName();
		EntryPoint derivedEventStream = kSession.getEntryPoint(derivedStreamName);

		for (Object eventObj : derivedEventStream.getObjects()) {
			ContextAssertion event = (ContextAssertion) eventObj;
			
			// if the objects have the same content
			if (event.allowsContentContinuity(derivedEvent)) {
				if (event.getAnnotations() != null) {
					// and they it includes the validity interval of the derived object
					if (event.getAnnotations().hasIncludedValidity(derivedEvent.getAnnotations())) {
						//System.out.println("[INFO] ::::::::::::::::::::: We have an insertion of an already existing event: " + derivedEvent);
						return true;
					}
				}
				else {
					return true;
				}
			}
		}
	    
		return false;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////// CONFLICT MANAGEMENT //////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void applyUniquenessConflictDecision(UniquenessConflictDecision decision, boolean existingAssertion) {

    }

    private void resolveConflict(ConstraintResult constraintResult, ContextAssertion assertion,
                                 FactHandle assertionHandle) {

	    if (constraintResult.hasValueViolations()) {
            // If we have value violations we currently ignore the extendedAssertion, deleting it
            // Further ValueConstraintViolation implementation may attempt to alter the value to
            // an "acceptable" one.
            kSession.getEntryPoint(assertion.getStreamName()).delete(assertionHandle);
        }
        else if (constraintResult.hasUniquenessViolations()) {
            // If the constraint check result contains a uniqueness violation, it can ONLY have
            // one of these => run it through the resolution service and interpret the results
            IUniquenessConstraintViolation ucv = constraintResult.getUniquenessViolation();
            UniquenessConflictDecision decision = constraintResolutionService.resolveConflict(ucv);

            if (!decision.keepNewAssertion()) {
                // remove the extended assertion from the entry point
                kSession.getEntryPoint(assertion.getStreamName()).delete(assertionHandle);
            }
            else {
                // we keep the new, extended assertion under a rectified form
                ContextAssertion rectifiedNewAssertion = decision.getRectifiedNewAssertion();

                // delete the extended assertion
                kSession.getEntryPoint(assertion.getStreamName()).delete(assertionHandle);
                trackedAssertionStore.removeAssertion(assertion);

                // insert the rectified extended assertion
                FactHandle rectifiedNewAssertionHandle =
                        kSession.getEntryPoint(assertion.getStreamName()).insert(rectifiedNewAssertion);

                trackedAssertionStore.trackAssertion(rectifiedNewAssertion, rectifiedNewAssertionHandle,
                    kSession.getEntryPoint(assertion.getStreamName()));


                if (decision.getRectifiedExistingAssertion() != null) {
                    // If the resolution decision involves altering the existing conflicting
                    // assertion, then replace that as well
                    ContextAssertion existingViolationAssertion = ucv.getExistingAssertion();

                    // 1) delete the current existing violating assertion
                    String entryPointName = existingViolationAssertion.getStreamName();
                    FactHandle fh = kSession.getEntryPoint(entryPointName)
                            .getFactHandle(existingViolationAssertion);

                    kSession.getEntryPoint(entryPointName).delete(fh);

                    // 2) insert the rectified existing violation assertion
                    ContextAssertion rectifiedExistingAssertion = decision.getRectifiedExistingAssertion();
                    DefaultAnnotationData ann = (DefaultAnnotationData) rectifiedExistingAssertion.getAnnotations();
                    ann.setLastUpdated(getCurrentTime());

                    FactHandle rectifiedExistingFh = kSession.getEntryPoint(entryPointName)
                            .insert(rectifiedExistingAssertion);

                    // 3) if the replaced existing violation assertion was also being tracked, then
                    // replace the entry in the trackedAssertionStore as well
                    TrackedAssertionData existingViolationData =
                            trackedAssertionStore.searchTrackedAssertionByContent(existingViolationAssertion);

                    if (existingViolationData != null) {
                        trackedAssertionStore.removeAssertion(existingViolationData);
                        trackedAssertionStore.trackAssertion(rectifiedExistingAssertion,
                            rectifiedExistingFh, kSession.getEntryPoint(entryPointName));
                    }
                }
            }
        }
        else {
            // TODO: It means we have one or several GENERAL constraints => we need to employ the
            // "sequential resolution mechanism"
        }
    }

    private void resolveConflict(ConstraintResult constraintResult, ContinuityResult continuityResult,
                                 FactHandle extendedAssertionHandle) {
        TrackedAssertionData existingAssertionData = continuityResult.getTrackedAssertionData(kSession);
        EntryPoint existingAssertionEntry = existingAssertionData.getExistingEventEntryPoint();

	    if (constraintResult.hasValueViolations()) {
            // If we have value violations we currently ignore the extendedAssertion, deleting it
            // Further ValueConstraintViolation implementation may attempt to alter the value to
            // an "acceptable" one.
            kSession.getEntryPoint(continuityResult.getExtendedEventStream())
                    .delete(extendedAssertionHandle);
        }
        else if (constraintResult.hasUniquenessViolations()) {
            // If the constraint check result contains a uniqueness violation, it can ONLY have
            // one of these => run it through the resolution service and interpret the results
            IUniquenessConstraintViolation ucv = constraintResult.getUniquenessViolation();
            UniquenessConflictDecision decision = constraintResolutionService.resolveConflict(ucv);

            if (!decision.keepNewAssertion()) {
                // If we are not keeping the new assertion
                // remove the extended assertion from the entry point
                kSession.getEntryPoint(continuityResult.getExtendedEventStream())
                        .delete(extendedAssertionHandle);
            }
            else {
                // We are keeping the new assertion - so update the trackedAssertionStore with the rectified version
                // (which may be the same as the inserted one if we are not keeping the existing one as well
                ContextAssertion rectifiedNewAssertion = decision.getRectifiedNewAssertion();
                if (rectifiedNewAssertion != null) {
                    // we keep the new, extended assertion under a rectified form
                    // delete the extended assertion
                    kSession.getEntryPoint(continuityResult.getExtendedEventStream())
                            .delete(extendedAssertionHandle);

                    // delete existing assertion - the one that is being extended
                    existingAssertionEntry.delete(continuityResult.getExistingAssertionHandle());
                    trackedAssertionStore.removeAssertion(existingAssertionData);

                    // insert the rectified extended assertion
                    FactHandle rectifiedNewAssertionHandle =
                            kSession.getEntryPoint(continuityResult.getExtendedEventStream()).insert(rectifiedNewAssertion);

                    trackedAssertionStore.trackAssertion(rectifiedNewAssertion, rectifiedNewAssertionHandle,
                            kSession.getEntryPoint(continuityResult.getExtendedEventStream()));
                }

                // Next check whether the existing assertion, with which there is a conflict, needs to be kept
                if (decision.keepExistingAssertion()) {
                    // If we keep the existing assertion too, there must be a rectification, even if it equals
                    // the current one

                    // If the resolution decision involves altering the existing conflicting
                    // assertion, then replace that as well
                    ContextAssertion existingViolationAssertion = ucv.getExistingAssertion();

                    // 1) delete the current existing violating assertion
                    String entryPointName = existingViolationAssertion.getStreamName();
                    FactHandle fh = kSession.getEntryPoint(entryPointName)
                            .getFactHandle(existingViolationAssertion);

                    kSession.getEntryPoint(entryPointName).delete(fh);

                    // 2) insert the rectified existing violation assertion
                    ContextAssertion rectifiedExistingAssertion = decision.getRectifiedExistingAssertion();
//                    DefaultAnnotationData ann = (DefaultAnnotationData) rectifiedExistingAssertion.getAnnotations();
//                    ann.setLastUpdated(getCurrentTime());

                    FactHandle rectifiedExistingFh = kSession.getEntryPoint(entryPointName)
                            .insert(rectifiedExistingAssertion);

                    // 3) if the replaced existing violation assertion was also being tracked, then
                    // replace the entry in the trackedAssertionStore as well
                    TrackedAssertionData existingViolationData =
                            trackedAssertionStore.searchTrackedAssertionByContent(existingViolationAssertion);

                    if (existingViolationData != null) {
                        trackedAssertionStore.removeAssertion(existingViolationData);
                        trackedAssertionStore.trackAssertion(rectifiedExistingAssertion,
                                rectifiedExistingFh, kSession.getEntryPoint(entryPointName));
                    }
                }
                else {
                    // If we are to delete the existing assertion - then just delete it from the entrypoint and
                    // check if it was also tracked
                    ContextAssertion existingViolationAssertion = ucv.getExistingAssertion();

                    // 1) delete the existing assertion
                    String entryPointName = existingViolationAssertion.getStreamName();
                    FactHandle fh = kSession.getEntryPoint(entryPointName)
                            .getFactHandle(existingViolationAssertion);
                    kSession.getEntryPoint(entryPointName).delete(fh);

                    // 2) if the replaced existing violation assertion was also being tracked, then
                    // replace the entry in the trackedAssertionStore as well
                    TrackedAssertionData existingViolationData =
                            trackedAssertionStore.searchTrackedAssertionByContent(existingViolationAssertion);
                    if (existingViolationData != null) {
                        trackedAssertionStore.removeAssertion(existingViolationData);
                    }
                }
            }
        }
        else {
            // TODO: It means we have one or several GENERAL constraints => we need to employ the
            // "sequential resolution mechanism"
        }
    }


//    @Override
//    public void conflictDetected(ValueConstraintViolation vcv) {
//
//    }
//
//    @Override
//    public void conflictDetected(UniquenessConstraintViolation ucv) {
//    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////// API MANAGEMENT ////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
	public void objectDeleted(ObjectDeletedEvent event) {
		//System.out.println("TRACKER DELETED EVENT object: " + event.getOldObject());
		//System.out.println("	HANDLE: " + event.getFactHandle());

        if (event.getOldObject() instanceof ContextAssertion) {
            ContextAssertion assertion = (ContextAssertion)event.getOldObject();
            if (!assertion.isAtomic())
                System.out.println("[CALLBACK DELETE] ************ DELETED assertion: " + assertion + "; FACT HANDLE: " + event.getFactHandle());
        }

		FactHandle deletedHandle = event.getFactHandle();
		if (deletedHandle instanceof EventFactHandle) {
		    // if we are deleting an event - ContextAssertion
            EventFactHandle deletedEventHandle = (EventFactHandle)deletedHandle;

            // only call the eventNotifier if we are deleting from
            // the default EntryPoint or a ContextAssertion specific one
            if (event.getOldObject() instanceof ContextAssertion
                    && !deletedEventHandle.getEntryPoint().getEntryPointId().equals(CONSTRAINT_STORE)) {

                ContextAssertion deletedAssertion = (ContextAssertion)event.getOldObject();

                if (trackedAssertionStore.untrack(deletedHandle, deletedAssertion)) {
                    trackedAssertionStore.markExpired(deletedHandle, deletedAssertion);
                }

                eventNotifier.notifyEventDeleted(deletedAssertion);
            }
		}
		else {
		    // if we are deleting a fact - EntityDescription
		    if (event.getOldObject() instanceof EntityDescription) {
                factNotifier.notifyFactDeleted((EntityDescription)event.getOldObject());
            }
        }
    }
	
	
    @Override
	public void objectInserted(ObjectInsertedEvent insertEvent) {
//		if (insertEvent.getObject() instanceof ContextAssertion) {
//
//		    ContextAssertion assertion = (ContextAssertion)insertEvent.getObject();
//		    if (!assertion.isAtomic())
//                System.out.println("[CALLBACK INSERTION] Inserted assertion: " + assertion + "; FACT HANDLE: " + insertEvent.getFactHandle());
//		}

        FactHandle insertHandle = insertEvent.getFactHandle();
        if (insertHandle instanceof EventFactHandle) {
            // if we are inserting an event - ContextAssertion
            EventFactHandle insertEventHandle = (EventFactHandle)insertHandle;

            if (insertEvent.getObject() instanceof ContextAssertion
                    && !insertEventHandle.getEntryPoint().getEntryPointId().equals(CONSTRAINT_STORE)) {
                //System.out.println("TRACKER INSERTED EVENT object: " + insertEvent.getObject());
                eventNotifier.notifyEventInserted((ContextAssertion)insertEvent.getObject());
            }
        }
    	else {
            // if we are inserting a fact - EntityDescription
            if (insertEvent.getObject() instanceof EntityDescription)
                factNotifier.notifyFactInserted((EntityDescription) insertEvent.getObject());
        }
    }

    
    @Override
	public void objectUpdated(ObjectUpdatedEvent event) {
    }

	@Override
    public void addEventListener(ContextAssertionListener eventListener) {
	    eventNotifier.addEventListener(eventListener);
    }

	@Override
    public void removeEventListener(ContextAssertionListener eventListener) {
	    eventNotifier.removeEventListener(eventListener);
    }

	@Override
    public void addFactListener(EntityDescriptionListener factListener) {
	    factNotifier.addfactListener(factListener);
    }

	@Override
    public void removeFactListener(EntityDescriptionListener factListener) {
	    factNotifier.removefactListener(factListener);
    }
}
