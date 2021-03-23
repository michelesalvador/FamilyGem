package app.familygem.constants;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Person;

public enum Gender {

	NONE, // No SEX tag
	MALE, // 'SEX M'
	FEMALE, // 'SEX F'
	UNDEFINED, // 'SEX U'
	OTHER; // Some other value

	// Find the gender of a Person
	public static Gender getGender(Person person) {
		for( EventFact fatto : person.getEventsFacts() ) {
			if( fatto.getTag()!=null && fatto.getTag().equals("SEX") ) {
				if( fatto.getValue() == null )
					return OTHER;  // There is 'SEX' tag but the value is empty
				else {
					switch( fatto.getValue() ) {
						case "M": return MALE;
						case "F": return FEMALE;
						case "U": return UNDEFINED;
						default: return OTHER;
					}
				}
			}
		}
		return NONE; // There is no 'SEX' tag
	}

	public static boolean isMale(Person person) {
		return getGender(person) == MALE;
	}

	public static boolean isFemale(Person person) {
		return getGender(person) == FEMALE;
	}
}
