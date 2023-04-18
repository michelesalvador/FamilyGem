package app.familygem.util

import android.widget.Toast
import app.familygem.Global
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Utils {
    fun string(id: Int): String {
        return Global.context.getString(id)
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
