package app.familygem.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Level
import app.familygem.detail.NoteActivity
import app.familygem.profile.ProfileActivity
import app.familygem.visitor.NoteReferences
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.NoteRef

fun Note.getValue(level: Level = Level.DETAILED): String? {
    return value?.replace("\n+".toRegex(), if (level == Level.DETAILED || level == Level.MEDIUM) "\n" else " ")
}

object NoteUtil {

    /**
     * Creates a new shared note, attached or not to a given container.
     * @param container If not null the Note will be attached to it
     */
    fun createSharedNote(context: Context, container: NoteContainer?) {
        val note = Note()
        val id = U.newID(Global.gc, Note::class.java)
        note.id = id
        note.value = ""
        Global.gc.addNote(note)
        if (container != null) {
            val noteRef = NoteRef()
            noteRef.ref = id
            container.addNoteRef(noteRef)
        }
        TreeUtil.save(true, note)
        Memory.setLeader(note)
        context.startActivity(Intent(context, NoteActivity::class.java))
    }

    /** Places into layout all the notes of a given note container. */
    @JvmOverloads
    fun placeNotes(layout: LinearLayout, container: NoteContainer, level: Level = Level.DETAILED, gedcom: Gedcom = Global.gc) {
        if (level == Level.DETAILED && (container.noteRefs.isNotEmpty() || container.notes.isNotEmpty())) {
            val titleView = LayoutInflater.from(layout.context).inflate(R.layout.notes_title, layout, false)
            layout.addView(titleView)
            if (layout.context is ProfileActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // profile.FactsFragment
                (titleView as TextView).setTextAppearance(R.style.AppTheme_LittleTitle)
            }
        }
        container.noteRefs.forEach { ref -> ref.getNote(gedcom)?.let { placeNote(layout, it, ref, level) } }
        container.notes.forEach { placeNote(layout, it, null, level) }
    }

    /**
     * Places into layout a single note with different level of detail.
     * @param ref Will be used by shared note context menu
     */
    fun placeNote(layout: LinearLayout, note: Note, ref: NoteRef?, level: Level) {
        val context = layout.context
        val noteView: View = LayoutInflater.from(context).inflate(R.layout.note_layout, layout, false)
        layout.addView(noteView)
        val textView = noteView.findViewById<TextView>(R.id.note_text)
        textView.text = note.getValue(level)
        val refsView = noteView.findViewById<TextView>(R.id.note_references)
        if ((level == Level.DETAILED || level == Level.MEDIUM) && note.id != null) {
            val references = NoteReferences(Global.gc, note.id, false)
            refsView.text = references.count.toString()
            noteView.setTag(R.id.tag_ref, ref)
        } else refsView.visibility = View.GONE
        val sourceCiteNum = note.sourceCitations.size
        val sourceCiteView = noteView.findViewById<TextView>(R.id.note_citationsNumber)
        if (Global.settings.expert && level == Level.SMALL && sourceCiteNum > 0) sourceCiteView.text = sourceCiteNum.toString()
        else sourceCiteView.visibility = View.GONE
        if (level == Level.DETAILED) {
            noteView.setTag(R.id.tag_object, note)
            if (context is ProfileActivity) { // profile.FactsFragment
                context.getPageFragment(1).registerForContextMenu(noteView)
            } else if (layout.id != R.id.cabinet_box) // In all detail activities but not in cabinet
                (context as AppCompatActivity).registerForContextMenu(noteView)
        } else if (level == Level.MEDIUM) {
            textView.textSize = 15F
            textView.maxLines = 3
        } else { // Level.SMALL
            textView.textSize = 14F
            textView.maxLines = 1
        }
        if ((level == Level.DETAILED || level == Level.MEDIUM) && layout.id != R.id.cabinet_box) {
            val sourceBox = noteView.findViewById<LinearLayout>(R.id.note_sources)
            SourceCitationUtil.placeSourceCitations(sourceBox, note, level.lower())
        }
        if (level == Level.DETAILED || layout.id == R.id.cabinet_box) {
            noteView.setOnClickListener {
                if (note.id != null) Memory.setLeader(note)
                else Memory.add(note)
                context.startActivity(Intent(context, NoteActivity::class.java))
            }
        }
    }
}
