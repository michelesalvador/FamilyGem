package app.familygem.util

import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.familygem.Memory
import app.familygem.ProfileActivity
import app.familygem.R
import app.familygem.detail.NoteActivity
import org.folg.gedcom.model.Note

object NoteUtil {

    /**
     * Places on a layout a single note detailed or not.
     */
    fun placeNote(layout: LinearLayout, note: Note, detailed: Boolean) {
        val context = layout.context
        val noteView: View = LayoutInflater.from(context).inflate(R.layout.note_layout, layout, false)
        layout.addView(noteView)
        val textView = noteView.findViewById<TextView>(R.id.note_text)
        textView.text = note.value
        val sourceCiteNum = note.sourceCitations.size
        val sourceCiteView = noteView.findViewById<TextView>(R.id.note_sources)
        if (sourceCiteNum > 0 && detailed) sourceCiteView.text = sourceCiteNum.toString()
        else sourceCiteView.visibility = View.GONE
        textView.ellipsize = TextUtils.TruncateAt.END
        if (detailed) {
            textView.maxLines = 10
            noteView.setTag(R.id.tag_object, note)
            if (context is ProfileActivity) { // ProfileFactsFragment
                context.getPageFragment(1).registerForContextMenu(noteView)
            } else if (layout.id != R.id.cabinet_box) // In all detail activities but not in cabinet
                (context as AppCompatActivity).registerForContextMenu(noteView)
            noteView.setOnClickListener {
                if (note.id != null) Memory.setLeader(note)
                else Memory.add(note)
                context.startActivity(Intent(context, NoteActivity::class.java))
            }
        } else {
            textView.maxLines = 3
        }
    }
}
