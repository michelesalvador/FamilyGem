package app.familygem.util

import android.widget.Toast
import app.familygem.Global
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The most generic utilities.
 */
object Utils {

    fun string(id: Int): String {
        return Global.context.getString(id)
    }

    /**
     * Lowercase string for all languages except German.
     */
    fun caseString(id: Int): String {
        return if (Locale.getDefault().language.equals("de")) Global.context.getString(id)
        else Global.context.getString(id).lowercase()
    }

    suspend fun toast(message: Int) {
        toast(string(message))
    }

    /**
     * Shows a toast message on the main coroutine context.
     */
    suspend fun toast(message: String?) {
        if (message != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(Global.context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
