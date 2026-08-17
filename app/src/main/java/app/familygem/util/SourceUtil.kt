package app.familygem.util

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Image
import app.familygem.constant.Level
import app.familygem.detail.SourceActivity
import app.familygem.util.TreeUtil.save
import app.familygem.visitor.CountSourceCitations
import app.familygem.visitor.ListOfSourceCitations
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer

fun Source.getMainText(level: Level = Level.DETAILED): String {
    fun reduce(str: String): String {
        return str.replace("\n+".toRegex(), if (level == Level.DETAILED) "\n" else " ")
    }

    val builder = StringBuilder()
    val divider = if (level == Level.DETAILED || level == Level.MEDIUM) "\n" else " "
    if (abbreviation != null) builder.append(abbreviation).append(divider)
    if (title != null) builder.append(reduce(title)).append(divider)
    if (author != null) builder.append(reduce(author)).append(divider)
    if (publicationFacts != null) builder.append(reduce(publicationFacts)).append(divider)
    if (text != null) builder.append(reduce(text)).append(divider)
    if (referenceNumber != null) builder.append(referenceNumber)
    return builder.toString().trim { it <= ' ' }
}

object SourceUtil {

    /** Creates a new source, adding to the [container] if provided. */
    fun createSource(context: Context, container: ExtensionContainer? = null) {
        val source = Source()
        source.id = U.newID(Global.gc, Source::class.java)
        source.title = ""
        source.text = ""
        Global.gc.addSource(source)
        if (container != null) {
            val sourceCitation = SourceCitation()
            sourceCitation.ref = source.id
            if (container is Note) container.addSourceCitation(sourceCitation)
            else (container as SourceCitationContainer).addSourceCitation(sourceCitation)
        }
        save(true, source)
        Memory.setLeader(source)
        context.startActivity(Intent(context, SourceActivity::class.java))
    }

    /**
     * Deletes a source, the reference in all SourceCitations and the empty SourceCitations.
     * Not-empty SourceCitations remain as Note-source.
     * @return An array of modified leader objects.
     */
    fun deleteSource(source: Source): Array<Any?> {
        val citations = ListOfSourceCitations(Global.gc, source.id)
        for (triplet in citations.list) {
            triplet.citation.apply {
                ref = null
                val hasSomething = page != null || date != null || text != null || quality != null ||
                        getAllNotes(Global.gc).isNotEmpty() || getAllMedia(Global.gc).isNotEmpty() || extensions.isNotEmpty()
                if (!hasSomething) {
                    val container = triplet.container
                    val list = if (container is Note) container.sourceCitations
                    else (container as SourceCitationContainer).sourceCitations
                    list.remove(this)
                    if (list.isEmpty()) {
                        if (container is Note) container.sourceCitations = null
                        else (container as SourceCitationContainer).sourceCitations = null
                    }
                }
            }
        }
        Global.gc.sources.remove(source)
        if (Global.gc.sources.isEmpty()) Global.gc.sources = null
        Global.gc.createIndexes()
        Memory.setInstanceAndAllSubsequentToNull(source)
        return citations.getProgenitors()
    }

    /** Creates in layout a reference to a source, more detailed or not. */
    fun placeSource(layout: LinearLayout, source: Source, level: Level = Level.DETAILED) {
        val sourceView = LayoutInflater.from(layout.context).inflate(R.layout.source_layout, layout, false) as LinearLayout
        layout.addView(sourceView)
        val textView = sourceView.findViewById<TextView>(R.id.source_text)
        val text = source.getMainText(level)
        if (text.isNotBlank()) textView.text = text else textView.visibility = View.GONE
        when (level) {
            Level.DETAILED -> {
                val titleView = LayoutInflater.from(layout.context).inflate(R.layout.notes_title, layout, false) as TextView
                titleView.setText(R.string.source)
                layout.addView(titleView, 0)
                MediaUtil.placeMedia(sourceView, source, false)
                NoteUtil.placeNotes(sourceView, source, Level.MEDIUM)
                sourceView.setTag(R.id.tag_object, source)
                (layout.context as AppCompatActivity).registerForContextMenu(sourceView)
            }
            Level.MEDIUM -> {
                textView.textSize = 15F
                textView.maxLines = 3
                if (layout.id != R.id.cabinet_box) {
                    NoteUtil.placeNotes(sourceView, source, Level.SMALL)
                }
            }
            else -> { // Level.SMALL
                textView.textSize = 14F
                textView.maxLines = 1
            }
        }
        if (level == Level.DETAILED || level == Level.MEDIUM) {
            val citationsCount = CountSourceCitations(source.id)
            Global.gc.accept(citationsCount)
            val citationsView = sourceView.findViewById<TextView>(R.id.source_citationsNum)
            citationsView.text = citationsCount.count.toString()
            citationsView.visibility = View.VISIBLE
        }
        if (level == Level.MEDIUM || level == Level.SMALL) {
            source.getAllMedia(Global.gc).firstOrNull()?.let {
                val imageView = sourceView.findViewById<ImageView>(R.id.source_image)
                FileUtil.showImage(it, imageView, Image.SOURCE)
                imageView.visibility = View.VISIBLE
                if (level == Level.SMALL) {
                    imageView.layoutParams.width = U.dpToPx(15F)
                    imageView.layoutParams.height = U.dpToPx(20F)
                }
            }
        }
        if (level == Level.DETAILED || layout.id == R.id.cabinet_box) {
            sourceView.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = U.dpToPx(6F) }
            sourceView.setOnClickListener {
                Memory.setLeader(source)
                layout.context.startActivity(Intent(layout.context, SourceActivity::class.java))
            }
        }
    }
}
