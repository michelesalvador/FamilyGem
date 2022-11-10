package app.familygem.constant;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Person;

public enum Gender {

	NONE, // No SEX tag
	MALE, // 'SEX M'
	FEMALE, // 'SEX F'
	UNKNOWN, // 'SEX U'
	OTHER; // Some other value

	/**
	 * Finds the gender of [person]
	 * */
	public static Gender getGender(Person person) {
		for( EventFact fact : person.getEventsFacts() ) {
			if( fact.getTag() != null && fact.getTag().equals("SEX") ) {
				if( fact.getValue() == null )
					return OTHER;  // There is 'SEX' tag but the value is empty
				else {
					switch( fact.getValue() ) {
						case "M": return MALE;
						case "F": return FEMALE;
						case "U": return UNKNOWN;
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
