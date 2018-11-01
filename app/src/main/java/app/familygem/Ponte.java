package app.familygem;

import java.util.Hashtable;

public class Ponte {

	private static Ponte instance;
	private Hashtable<String, Object> hash;

	private Ponte() {
		hash = new Hashtable<String, Object>();
	}

	private static Ponte getInstance() {
		if( instance == null ) {
			instance = new Ponte();
		}
		return instance;
	}

	public static void manda( Object object, String key ) {
		getInstance().hash.put(key, object);
	}

	public static Object ricevi( String key ) {
		Ponte helper = getInstance();
		Object data = helper.hash.get(key);
		helper.hash.remove(key);
		helper = null;
		return data;
	}
}