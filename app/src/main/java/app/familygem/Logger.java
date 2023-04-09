package app.familygem;

import android.util.Log;

public class Logger {

    /**
     * Logs everything with "mine" tag.
     */
    public static void l(Object... objects) {
        StringBuilder builder = new StringBuilder();
        for (Object object : objects)
            builder.append(object).append(" ");
        Log.v("mine", builder.toString());
    }
}
