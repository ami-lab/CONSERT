package org.aimas.consert.tests.casas.assertions;

import org.aimas.consert.model.annotations.AnnotationData;
import org.aimas.consert.model.content.UnaryContextAssertion;
import org.aimas.consert.tests.casas.entities.StringLiteral;

public class PersonLocation extends UnaryContextAssertion {
	private String location;
	
	public PersonLocation() {
	}
	
	public PersonLocation(String location, AnnotationData annotations) {
		super(new StringLiteral(location), AcquisitionType.SENSED, annotations);
		
		this.location = location;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
		setInvolvedEntity(new StringLiteral(location));
	}
}