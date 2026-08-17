package app.familygem.util

import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import app.familygem.DateConverter
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Image
import app.familygem.constant.Level
import app.familygem.detail.SourceCitationActivity
import app.familygem.profile.ProfileActivity
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer

fun SourceCitation.getText(level: Level = Level.DETAILED): String {
    fun reduce(str: String): String {
        return str.replace("\n+".toRegex(), if (level == Level.DETAILED) "\n" else " ")
    }

    val builder = StringBuilder()
    val divider = if (level == Level.DETAILED || level == Level.MEDIUM) "\n" else " "
    if (value != null) builder.append(value).append(divider)
    if (page != null) builder.append(page).append(divider)
    if (date != null) builder.append(DateConverter(date).writeDateLong()).append(divider)
    if (text != null) builder.append(reduce(text))
    return builder.toString().trim { it <= ' ' }
}

object SourceCitationUtil {

    /** Places into layout the source citations of a given container, with different level of detail. */
    @JvmOverloads
    fun placeSourceCitations(layout: LinearLayout, container: ExtensionContainer, level: Level = Level.DETAILED) {
        if (Global.settings.expert) {
            val sourceCitations = if (container is Note) container.sourceCitations // Note doesn't extend SourceCitationContainer
            else (container as SourceCitationContainer).sourceCitations
            if (level == Level.DETAILED && sourceCitations.isNotEmpty()) {
                val titleView = LayoutInflater.from(layout.context).inflate(R.layout.notes_title, layout, false) as TextView
                titleView.setText(R.string.sources)
                layout.addView(titleView)
                if (layout.context is ProfileActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // profile.FactsFragment
                    titleView.setTextAppearance(R.style.AppTheme_LittleTitle)
                }
            }
            for (citation in sourceCitations) {
                val citationView = LayoutInflater.from(layout.context).inflate(R.layout.source_citation_layout, layout, false) as LinearLayout
                layout.addView(citationView)
                if (level != Level.SMALL) {
                    val source = citation.getSource(Global.gc)
                    if (source != null) {
                        val sourceView = citationView.findViewById<LinearLayout>(R.id.sourceCitation_source)
                        SourceUtil.placeSource(sourceView, source, level.lower())
                    }
                }
                val boxView = citationView.findViewById<RelativeLayout>(R.id.sourceCitation_box)
                val textView = citationView.findViewById<TextView>(R.id.sourceCitation_text)
                val text = citation.getText(level)
                if (text.isNotBlank()) {
                    boxView.visibility = View.VISIBLE
                    textView.text = text
                    if (level == Level.MEDIUM) {
                        textView.maxLines = 3
                        textView.textSize = 15F
                    } else if (level == Level.SMALL) {
                        textView.maxLines = 1
                        textView.textSize = 14F
                    }
                }
                if (level == Level.DETAILED) {
                    MediaUtil.placeMedia(citationView, citation, false)
                    citationView.setTag(R.id.tag_object, citation)
                    if (layout.context is ProfileActivity) { // profile.FactsFragment
                        (layout.context as ProfileActivity).getPageFragment(1).registerForContextMenu(citationView)
                    } else // A detail activity
                        (layout.context as AppCompatActivity).registerForContextMenu(citationView)
                    citationView.setOnClickListener {
                        val intent = Intent(layout.context, SourceCitationActivity::class.java)
                        Memory.add(citation)
                        layout.context.startActivity(intent)
                    }
                } else { // Level.MEDIUM or Level.SMALL
                    citationView.setPadding(U.dpToPx(3F))
                    citation.getAllMedia(Global.gc).firstOrNull()?.let {
                        boxView.visibility = View.VISIBLE
                        val imageView = citationView.findViewById<ImageView>(R.id.sourceCitation_image)
                        FileUtil.showImage(it, imageView, Image.SOURCE)
                        imageView.visibility = View.VISIBLE
                        if (level == Level.SMALL) {
                            imageView.layoutParams.width = U.dpToPx(15F)
                            imageView.layoutParams.height = U.dpToPx(20F)
                        }
                    }
                }
                NoteUtil.placeNotes(citationView, citation, level.lower())
            }
        }
    }
}
