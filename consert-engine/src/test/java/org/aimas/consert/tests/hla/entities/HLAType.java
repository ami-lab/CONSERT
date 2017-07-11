package org.aimas.consert.tests.hla.entities;

import org.aimas.consert.model.content.ContextEntity;
import org.cyberborean.rdfbeans.annotations.RDF;
import org.cyberborean.rdfbeans.annotations.RDFBean;
import org.cyberborean.rdfbeans.annotations.RDFNamespaces;
import org.cyberborean.rdfbeans.annotations.RDFSubject;


@RDFNamespaces(
	    "hla = http://example.org/hlatest/" 
)
@RDFBean("hla:HLAType")
public class HLAType implements ContextEntity {
    /*DISCUSSING, EXERCISE, WORKING, DINING;*/
	
	String type;
	
	public HLAType() {}
	
	public HLAType(String type) {
    	this.type = type;
    }
    
	@RDF("rdfs:label")
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	@Override
    public boolean isLiteral() {
        return true;
    }

	@Override
    public Object getValue() {
        return type;
    }

	@Override
	@RDFSubject(prefix = "hla:")
    public String getEntityId() {
		return type;
    }
	
	public void setEntityId(String entityIdentifier) {
		this.type = entityIdentifier;
	}

	@Override
    public int hashCode() {
	    return type.hashCode();
    }

	@Override
    public boolean equals(Object obj) {
	    if (this == obj)
		    return true;
	    if (obj == null)
		    return false;
	    if (getClass() != obj.getClass())
		    return false;
	    HLAType other = (HLAType) obj;
	    if (type == null) {
		    if (other.type != null)
			    return false;
	    }
	    else if (!type.equals(other.type))
		    return false;
	    return true;
    }

	@Override
    public String toString() {
	    return "HLAType [type=" + type + "]";
    }
	
	
}