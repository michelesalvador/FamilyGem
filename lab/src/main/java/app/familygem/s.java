package app.familygem;

// Scrivi in breve

public class s {

	public static void l( Object... objects) {
		String str = "";
		for(Object obj : objects)
			str += obj + " ";
		System.out.println(".\t" + str);
	}
	
	public static void p( Object parola ) {
		System.out.print( parola );
	}
	
}
