package app.familygem.constant

/** GEDCOM date types. */
enum class Kind(val prefix: String) {
    EXACT(""),
    APPROXIMATE("ABT"), CALCULATED("CAL"), ESTIMATED("EST"),
    AFTER("AFT"), BEFORE("BEF"), BETWEEN_AND("BET"),
    FROM("FROM"), TO("TO"), FROM_TO("FROM"),
    PHRASE("("); // TODO: Manage also the "INT" (interpreted) prefix.
}
