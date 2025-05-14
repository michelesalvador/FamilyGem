package app.familygem.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.familygem.Global
import app.familygem.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

fun InputStream.copyTo(out: OutputStream, onCopy: (totalBytes: Long) -> Unit) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytes: Int
    var totalBytes: Long = 0
    while (read(buffer).also { bytes = it } != -1) {
        out.write(buffer, 0, bytes)
        totalBytes += bytes
        onCopy(totalBytes)
    }
}

fun Float.toPxFloat(): Float {
    return this * Global.context.resources.displayMetrics.density + 0.5F
}

/** The most generic utilities. */
object Util {

    fun string(id: Int): String {
        return Global.context.getString(id)
    }

    /** Lowercase string for all languages except German. */
    fun caseString(id: Int): String {
        return if (Locale.getDefault().language.equals("de")) Global.context.getString(id)
        else Global.context.getString(id).lowercase()
    }

    suspend fun toast(message: Int) {
        toast(string(message))
    }

    /** Shows a toast message on the main coroutine context. */
    suspend fun toast(message: String?) {
        withContext(Dispatchers.Main) {
            Toast.makeText(Global.context, message ?: string(R.string.something_wrong), Toast.LENGTH_LONG).show()
        }
    }

    fun confirmDelete(context: Context, action: () -> Unit) {
        AlertDialog.Builder(context).setMessage(R.string.sure_delete)
            .setPositiveButton(R.string.yes) { _, _ -> action() }
            .setNeutralButton(android.R.string.cancel, null).show()
    }

    /**
     * Returns encrypted shared preferences for API 23+, otherwise normal shared preferences.
     * @return Null in case of error
     */
    fun getSharedPreferences(context: Context): SharedPreferences? {
        val fileName = "credential"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                EncryptedSharedPreferences.create(
                    context, fileName, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(fileName) // Deletes them since they are probably invalid
                }
                e.printStackTrace()
                null
            }
        } else {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }
    }
}
