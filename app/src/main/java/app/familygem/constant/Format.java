package app.familygem.constant;

/**
 * All of the formats a Date can be displayed as
 * */
public class Format {
	public static final String[] PATTERNS = { "d MMM yyy", "d M yyy", "MMM yyy", "M yyy", "d MMM", "yyy" };
	public static final String D_M_Y = PATTERNS[0];
	public static final String D_m_Y = PATTERNS[1];
	public static final String M_Y = PATTERNS[2];
	public static final String m_Y = PATTERNS[3];
	public static final String D_M = PATTERNS[4];
	public static final String Y = PATTERNS[5];
	public static final String EMPTY = "";
}
