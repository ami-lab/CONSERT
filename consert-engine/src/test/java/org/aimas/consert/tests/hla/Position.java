package org.aimas.consert.tests.hla;

import org.aimas.consert.model.AnnotationData;
import org.aimas.consert.model.BinaryContextAssertion;
import org.aimas.consert.model.ContextAssertion;
import org.aimas.consert.model.ContextEntity;

/*
 *Class for modeling a positioning event
*/
public class Position extends BinaryContextAssertion {
    
	@Override
    public int hashCode() {
	    final int prime = 31;
	    int result = 1;
	    result = prime * result + ((person == null) ? 0 : person.hashCode());
	    result = prime * result + ((type == null) ? 0 : type.hashCode());
	    return result;
    }

	public enum Type implements ContextEntity {
        WORK_AREA {
            @Override
            public Object getValue() {
                return WORK_AREA;
            }
        },
        DINING_AREA {
            @Override
            public Object getValue() {
                return DINING_AREA;
            }
        },
        SITTING_AREA {
            @Override
            public Object getValue() {
                return SITTING_AREA;
            }
        },
        CONFERENCE_AREA {
            @Override
            public Object getValue() {
                return CONFERENCE_AREA;
            }
        },
        ENTERTAINMENT_AREA {
            @Override
            public Object getValue() {
                return ENTERTAINMENT_AREA;
            }
        },
        SNACK_AREA {
            @Override
            public Object getValue() {
                return SNACK_AREA;
            }
        },
        EXERCISE_AREA {
            @Override
            public Object getValue() {
                return EXERCISE_AREA;
            }
        },
        HYGENE_AREA {
            @Override
            public Object getValue() {
                return HYGENE_AREA;
            }
        };

		@Override
        public boolean isLiteral() {
	        return true;
        }
    }

    Person person;                  /* The person which is implied in the positioning event */
    Type type;                      /* Positioning type*/

    public Position() {
    }

    public Position(Person person, Type type, AnnotationData annotations) {
        super(person, type, AcquisitionType.SENSED, annotations);
    	this.person = person;
        this.type = type;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
    
    public int getContentHash() {
	    final int prime = 31;
	    int result = 1;
	    result = prime * result + ((person == null) ? 0 : person.hashCode());
	    result = prime * result + ((type == null) ? 0 : type.hashCode());
	    return result;
    }
    
    @Override
    public String toString() {
        return "Position [" + "person=" + person + ", type=" + type + ", annotations=" + annotationData + "]";
    }
    
    @Override
    public boolean allowsContentContinuity(ContextAssertion event) {
    	Position otherEvent = (Position)event;
    	
    	if (type == otherEvent.getType() && person.equals(otherEvent.getPerson())) {
    		return true;
    	}
    	
    	return false;
    }

	@Override
	public String getStreamName() {
		return Position.class.getSimpleName() + "Stream";
	}
	
	public String getExtendedStreamName() {
		return "Extended" + getStreamName();
	}
}
