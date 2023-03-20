package app.familygem;

import android.util.Log;

/**
 * Writes everything.
 */
public class Logger {

    public static void l(Object... objects) {
        StringBuilder builder = new StringBuilder();
        if (objects != null) {
            for (Object object : objects)
                builder.append(object).append(" ");
        } else
            builder.append((String)null);
        Log.v("mine", builder.toString());
    }
}
