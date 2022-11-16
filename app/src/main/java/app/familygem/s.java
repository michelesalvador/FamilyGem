package app.familygem;

/**
 * Shorthand wrapper for logging
 * */
public class s {

	public static void l(Object... objects) {
		StringBuilder str = new StringBuilder();
		if( objects != null ) {
			for( Object obj : objects )
				str.append(obj).append(" ");
		} else
			str.append((String) null);
		System.out.println(".\t" + str);
		//android.util.Log.v("v", str);
	}
	
	public static void p( Object word ) {
		System.out.print( word );
	}
	
}
