package app.familygem.util

import android.view.View
import android.widget.TextView
import app.familygem.Global
import app.familygem.visitor.MediaReferences
import org.folg.gedcom.model.Media

object MediaUtil {

    /**
     * Setups the layout of a media in gallery.
     */
    fun furnishMedia(media: Media, textView: TextView, numberView: TextView) {
        // File title and name
        var text = media.title ?: ""
        if (Global.settings.expert && media.file != null) {
            var file = media.file
            file = file.replace('\\', '/')
            if (file.lastIndexOf('/') > -1) {
                if (file.length > 1 && file.endsWith("/")) // Removes last slash
                    file = file.substring(0, file.length - 1)
                file = file.substring(file.lastIndexOf('/') + 1)
            }
            text += "\n$file"
        }
        if (text.isEmpty()) textView.visibility = View.GONE
        else textView.text = text.trim('\n', ' ')
        // Usage number
        if (media.id != null) {
            val mediaReferences = MediaReferences(Global.gc, media, false)
            numberView.text = mediaReferences.num.toString()
            numberView.visibility = View.VISIBLE
        } else numberView.visibility = View.GONE
    }
}
