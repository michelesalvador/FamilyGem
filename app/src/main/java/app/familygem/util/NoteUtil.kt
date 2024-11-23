package app.familygem.util

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.detail.NoteActivity
import app.familygem.profile.ProfileActivity
import app.familygem.visitor.NoteReferences
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.NoteRef

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
    fun placeNotes(layout: LinearLayout, container: NoteContainer, detailed: Boolean = true, gedcom: Gedcom = Global.gc) {
        container.noteRefs.forEach { ref -> ref.getNote(gedcom)?.let { placeNote(layout, it, ref, detailed) } }
        container.notes.forEach { placeNote(layout, it, null, detailed) }
    }

    /**
     * Places into layout a single note detailed or not.
     * @param ref Will be used by shared note context menu
     */
    fun placeNote(layout: LinearLayout, note: Note, ref: NoteRef?, detailed: Boolean) {
        val context = layout.context
        val noteView: View = LayoutInflater.from(context).inflate(R.layout.note_layout, layout, false)
        layout.addView(noteView)
        val textView = noteView.findViewById<TextView>(R.id.note_text)
        textView.text = note.value
        val refsView = noteView.findViewById<TextView>(R.id.note_references)
        if (detailed && note.id != null) {
            val references = NoteReferences(Global.gc, note.id, false)
            refsView.text = references.count.toString()
            noteView.setTag(R.id.tag_ref, ref)
        } else refsView.visibility = View.GONE
        val sourceCiteNum = note.sourceCitations.size
        val sourceCiteView = noteView.findViewById<TextView>(R.id.note_sources)
        if (detailed && Global.settings.expert && sourceCiteNum > 0) sourceCiteView.text = sourceCiteNum.toString()
        else sourceCiteView.visibility = View.GONE
        textView.ellipsize = TextUtils.TruncateAt.END
        if (detailed) {
            textView.maxLines = 10
            noteView.setTag(R.id.tag_object, note)
            if (context is ProfileActivity) { // profile.FactsFragment
                context.getPageFragment(1).registerForContextMenu(noteView)
            } else if (layout.id != R.id.cabinet_box) // In all detail activities but not in cabinet
                (context as AppCompatActivity).registerForContextMenu(noteView)
            noteView.setOnClickListener {
                if (note.id != null) Memory.setLeader(note)
                else Memory.add(note)
                context.startActivity(Intent(context, NoteActivity::class.java))
            }
        } else {
            textView.textSize = 15F
            textView.maxLines = 3
        }
    }
}
