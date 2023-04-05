package app.familygem.constant;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Person;

public enum Gender {

    NONE, // No SEX tag
    EMPTY, // SEX tag with empty value
    MALE, // SEX M
    FEMALE, // SEX F
    UNKNOWN, // SEX U
    OTHER; // Some other value

    /**
     * Finds the gender of a person.
     */
    public static Gender getGender(Person person) {
        for (EventFact fact : person.getEventsFacts()) {
            if ("SEX".equals(fact.getTag())) {
                if (fact.getValue() == null)
                    return EMPTY; // There is SEX tag but the value is empty
                else {
                    switch (fact.getValue()) {
                        case "M":
                            return MALE;
                        case "F":
                            return FEMALE;
                        case "U":
                            return UNKNOWN;
                        default:
                            return OTHER;
                    }
                }
            }
        }
        return NONE; // There is no SEX tag
    }

    public static boolean isMale(Person person) {
        return getGender(person) == MALE;
    }

    public static boolean isFemale(Person person) {
        return getGender(person) == FEMALE;
    }

    public static boolean isDefined(Person person) {
        Gender gender = getGender(person);
        return gender == MALE || gender == FEMALE || gender == OTHER;
    }
}
