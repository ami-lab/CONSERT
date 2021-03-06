package org.aimas.consert.tests.casas.assertions;

import org.aimas.consert.model.annotations.AnnotationData;
import org.aimas.consert.model.content.BinaryContextAssertion;
import org.aimas.consert.tests.casas.entities.NumericLiteral;
import org.aimas.consert.tests.casas.entities.StringLiteral;

public class Water extends BinaryContextAssertion {
	
	String sensorId;
	double value;
	
	public Water() {}
	
	public Water(String sensorId, double value, AnnotationData annotationData) {
	    super(new StringLiteral(sensorId), new NumericLiteral(value), AcquisitionType.SENSED, annotationData);
		
	    this.sensorId = sensorId;
	    this.value = value;
    }



	public String getSensorId() {
		return sensorId;
	}

	public void setSensorId(String sensorId) {
		this.sensorId = sensorId;
		setSubject(new StringLiteral(sensorId));
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
		setObject(new NumericLiteral(value));		
	}
	
	
}
