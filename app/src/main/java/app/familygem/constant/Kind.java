// GEDCOM date types

package app.familygem.constant;

public enum Kind {

	EXACT(""),
	APPROXIMATE("ABT"), CALCULATED("CAL"), ESTIMATED("EST"),
	AFTER("AFT"), BEFORE("BEF"), BETWEEN_AND("BET"),
	FROM("FROM"), TO("TO"), FROM_TO("FROM"),
	PHRASE("("); // Todo "INT"

	public String prefix;

	Kind(String prefix) {
		this.prefix = prefix;
	}
}
