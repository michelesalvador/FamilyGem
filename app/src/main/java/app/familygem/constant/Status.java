package app.familygem.constant;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
/**
 * Family situation
 * */
public enum Status {

	NONE, // Generic relationship
	MARRIED, DIVORCED, SEPARATED;

	/**
	 * Finds the status of [family]
	 * */
	public static Status getStatus(Family family) {
		Status status = NONE;
		if( family != null ) {
			for( EventFact event : family.getEventsFacts() ) {
				String tag = event.getTag();
				switch( tag ) {
					case "MARR":
						String type = event.getType();
						if( type == null || type.isEmpty() || type.equals("marriage")
								|| type.equals("civil") || type.equals("religious") || type.equals("common law") )
							status = MARRIED;
						else
							status = NONE;
						break;
					case "MARB":
					case "MARC":
					case "MARL":
					case "MARS":
						status = MARRIED;
						break;
					case "DIV":
						status = status == MARRIED ? DIVORCED : SEPARATED;
						break;
				}
			}
		}
		return status;
	}
}
