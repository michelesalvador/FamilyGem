package app.familygem.util

import android.content.Intent
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.detail.SourceActivity
import app.familygem.main.SourcesFragment
import org.folg.gedcom.model.Source

object SourceUtil {

    /**
     * Creates in layout a clickable reference to a source, more detailed or not.
     */
    fun placeSource(layout: LinearLayout, source: Source, detailed: Boolean) {
        val sourceView = LayoutInflater.from(layout.context).inflate(R.layout.source_layout, layout, false)
        layout.addView(sourceView)
        val textView = sourceView.findViewById<TextView>(R.id.source_text)
        var text = ""
        if (detailed) {
            if (source.title != null) text = source.title + "\n"
            else if (source.abbreviation != null) text = source.abbreviation + "\n"
            if (source.type != null) text += source.type.replace("\n", " ") + "\n"
            if (source.publicationFacts != null) text += source.publicationFacts.replace("\n", " ") + "\n"
            if (source.text != null) text += source.text.replace("\n", " ")
            val otherBox = sourceView.findViewById<LinearLayout>(R.id.source_box)
            NoteUtil.placeNotes(otherBox, source, false)
            U.placeMedia(otherBox, source, false)
            sourceView.setTag(R.id.tag_object, source)
            (layout.context as AppCompatActivity).registerForContextMenu(sourceView)
        } else {
            textView.maxLines = 2
            text = SourcesFragment.titoloFonte(source)
        }
        textView.text = text.trim()
        sourceView.setOnClickListener {
            Memory.setLeader(source)
            layout.context.startActivity(Intent(layout.context, SourceActivity::class.java))
        }
    }
}
