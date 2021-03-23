package app.familygem;

// Scrivi in breve

public class s {

	public static void l(Object... objects) {
		String str = "";
		if( objects != null ) {
			for( Object obj : objects )
				str += obj + " ";
		} else
			str += objects;
		System.out.println(".\t" + str);
		//android.util.Log.v("v", str);
	}
	
	public static void p( Object parola ) {
		System.out.print( parola );
	}
	
}
