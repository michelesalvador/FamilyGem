package app.familygem.profile

import android.content.DialogInterface
import android.content.Intent
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.detail.EventActivity
import app.familygem.detail.ExtensionActivity
import app.familygem.detail.NameActivity
import app.familygem.util.ChangeUtil.placeChangeDate
import app.familygem.util.FamilyUtil.updateSpouseRoles
import app.familygem.util.NoteUtil
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import app.familygem.util.writeContent
import app.familygem.util.writeTitle
import org.folg.gedcom.model.EventFact
import org.folg.gedcom.model.GedcomTag
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.Name
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer
import java.util.Collections

class FactsFragment : BaseFragment() {

    override fun createContent() {
        if (prepareContent()) {
            person.names.forEach { placeEvent(layout, it.writeTitle(), U.firstAndLastName(it, " "), it) }
            person.eventsFacts.forEach { placeEvent(layout, it.writeTitle(), it.writeContent(), it) }
            U.findExtensions(person).forEach { placeEvent(layout, it.name, it.text, it.gedcomTag) }
            NoteUtil.placeNotes(layout, person)
            U.placeSourceCitations(layout, person)
            placeChangeDate(layout, person.change)
        }
    }

    /** Finds out if it is a name with name pieces or a suffix in the value. */
    private fun isNameComplex(name: Name): Boolean {
        return name.run {
            // Name pieces
            val hasPieces = prefix != null || given != null || surnamePrefix != null || surname != null || suffix != null
                    || fone != null || romn != null
            // Suffix after the surname
            var hasSuffix = false
            value?.trim()?.run { if (indexOf('/') > 0 && lastIndexOf('/') < length - 1) hasSuffix = true }
            hasPieces || hasSuffix
        }
    }

    private var chosenSex = 0

    private fun placeEvent(layout: LinearLayout, title: String, text: String, obj: Any) {
        val eventView = layoutInflater.inflate(R.layout.profile_facts_item, layout, false)
        layout.addView(eventView)
        eventView.findViewById<TextView>(R.id.profileFact_title).text = title
        val textView = eventView.findViewById<TextView>(R.id.profileFact_text)
        if (text.isEmpty()) textView.visibility = View.GONE
        else textView.text = text
        if (Global.settings.expert && obj is SourceCitationContainer) {
            val sourceCitations = obj.sourceCitations
            val sourceView = eventView.findViewById<TextView>(R.id.profileFact_sources)
            if (sourceCitations.isNotEmpty()) {
                sourceView.text = sourceCitations.size.toString()
                sourceView.visibility = View.VISIBLE
            }
        }
        val otherLayout = eventView.findViewById<LinearLayout>(R.id.profileFact_other)
        if (obj is NoteContainer) NoteUtil.placeNotes(otherLayout, obj, false)
        eventView.setTag(R.id.tag_object, obj)
        registerForContextMenu(eventView)
        if (obj is Name) {
            U.placeMedia(otherLayout, obj as MediaContainer, false)
            eventView.setOnClickListener {
                // If it is a complex name, suggests expert mode
                if (!Global.settings.expert && isNameComplex(obj)) {
                    AlertDialog.Builder(requireContext()).setMessage(R.string.complex_tree_advanced_tools)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            Global.settings.expert = true
                            Global.settings.save()
                            Memory.add(obj)
                            startActivity(Intent(context, NameActivity::class.java))
                        }.setNegativeButton(android.R.string.cancel) { _, _ ->
                            Memory.add(obj)
                            startActivity(Intent(context, NameActivity::class.java))
                        }.show()
                } else {
                    Memory.add(obj)
                    startActivity(Intent(context, NameActivity::class.java))
                }
            }
        } else if (obj is EventFact) {
            // Sex fact
            if (obj.tag != null && obj.tag == "SEX") {
                val sexes: MutableMap<String, String> = LinkedHashMap()
                sexes["M"] = getString(R.string.male)
                sexes["F"] = getString(R.string.female)
                sexes["U"] = getString(R.string.unknown)
                textView.text = text
                chosenSex = 0
                for ((key, value) in sexes) {
                    if (text == key) {
                        textView.text = value
                        break
                    }
                    chosenSex++
                }
                if (chosenSex > 2) chosenSex = -1
                eventView.setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setSingleChoiceItems(sexes.values.toTypedArray<String>(), chosenSex) { dialog: DialogInterface, item: Int ->
                            obj.value = ArrayList(sexes.keys)[item]
                            updateSpouseRoles(person)
                            dialog.dismiss()
                            refresh()
                            TreeUtil.save(true, person)
                        }.show()
                }
            } else { // All other events
                U.placeMedia(otherLayout, obj as MediaContainer, false)
                eventView.setOnClickListener {
                    Memory.add(obj)
                    startActivity(Intent(context, EventActivity::class.java))
                }
            }
        } else if (obj is GedcomTag) {
            eventView.setOnClickListener {
                Memory.add(obj)
                startActivity(Intent(context, ExtensionActivity::class.java))
            }
        }
    }

    // Context menu
    private lateinit var pieceView: View
    private lateinit var pieceObject: Any
    private var sharedNoteIndex = 0

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        pieceView = view
        pieceObject = view.getTag(R.id.tag_object)
        when (pieceObject) {
            is Name -> {
                menu.add(0, 200, 0, R.string.copy)
                if (person.names.indexOf(pieceObject) > 0)
                    menu.add(0, 201, 0, R.string.move_up)
                if (person.names.indexOf(pieceObject) < person.names.size - 1)
                    menu.add(0, 202, 0, R.string.move_down)
                menu.add(0, 203, 0, R.string.delete)
            }
            is EventFact -> {
                if (view.findViewById<View>(R.id.profileFact_text).visibility == View.VISIBLE)
                    menu.add(0, 210, 0, R.string.copy)
                if (person.eventsFacts.indexOf(pieceObject) > 0)
                    menu.add(0, 211, 0, R.string.move_up)
                if (person.eventsFacts.indexOf(pieceObject) < person.eventsFacts.size - 1)
                    menu.add(0, 212, 0, R.string.move_down)
                menu.add(0, 213, 0, R.string.delete)
            }
            is GedcomTag -> {
                menu.add(0, 220, 0, R.string.copy)
                menu.add(0, 221, 0, R.string.delete)
            }
            is Note -> {
                if (view.findViewById<TextView>(R.id.note_text).text.isNotEmpty())
                    menu.add(0, 225, 0, R.string.copy)
                val note = pieceObject as Note
                if (note.id != null) { // Shared note
                    sharedNoteIndex = person.noteRefs.indexOf(view.getTag(R.id.tag_ref))
                    if (sharedNoteIndex > 0)
                        menu.add(0, 226, 0, R.string.move_up)
                    if (sharedNoteIndex < person.noteRefs.size - 1)
                        menu.add(0, 227, 0, R.string.move_down)
                    menu.add(0, 228, 0, R.string.unlink)
                } else { // Simple note
                    if (person.notes.indexOf(note) > 0)
                        menu.add(0, 229, 0, R.string.move_up)
                    if (person.notes.indexOf(note) < person.notes.size - 1)
                        menu.add(0, 230, 0, R.string.move_down)
                }
                menu.add(0, 231, 0, R.string.delete)
            }
            is SourceCitation -> {
                menu.add(0, 240, 0, R.string.copy)
                if (person.sourceCitations.indexOf(pieceObject) > 0)
                    menu.add(0, 241, 0, R.string.move_up)
                if (person.sourceCitations.indexOf(pieceObject) < person.sourceCitations.size - 1)
                    menu.add(0, 242, 0, R.string.move_down)
                menu.add(0, 243, 0, R.string.delete)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            200, 210, 220 -> {
                U.copyToClipboard(
                    pieceView.findViewById<TextView>(R.id.profileFact_title).text,
                    pieceView.findViewById<TextView>(R.id.profileFact_text).text
                )
                return true
            }
            201 -> swapNames(-1)
            202 -> swapNames(1)
            203 -> return confirmDelete {
                person.names.remove(pieceObject)
                Memory.setInstanceAndAllSubsequentToNull(pieceObject)
            }
            211 -> swapEvents(-1)
            212 -> swapEvents(1)
            213 -> return confirmDelete {
                person.eventsFacts.remove(pieceObject)
                Memory.setInstanceAndAllSubsequentToNull(pieceObject)
            }
            221 -> return confirmDelete { U.deleteExtension(pieceObject as GedcomTag, person, pieceView) }
            225 -> {
                U.copyToClipboard(getText(R.string.note), pieceView.findViewById<TextView>(R.id.note_text).text)
                return true
            }
            226 -> swapSharedNotes(-1)
            227 -> swapSharedNotes(1)
            228 -> person.noteRefs.removeAt(sharedNoteIndex)
            229 -> swapNotes(-1)
            230 -> swapNotes(1)
            231 -> return confirmDelete(false) {
                val leaders = U.deleteNote(pieceObject as Note)
                TreeUtil.save(true, *leaders)
            }
            240 -> {
                U.copyToClipboard(
                    getText(R.string.source_citation),
                    ("${pieceView.findViewById<TextView>(R.id.source_text).text}\n" +
                            pieceView.findViewById<TextView>(R.id.sourceCitation_text).text)
                )
                return true
            }
            241 -> swapSourceCitations(-1)
            242 -> swapSourceCitations(1)
            243 -> return confirmDelete {
                person.sourceCitations.remove(pieceObject)
                Memory.setInstanceAndAllSubsequentToNull(pieceObject)
            }
            else -> return false
        }
        TreeUtil.save(true, person)
        refresh()
        return true
    }

    private fun confirmDelete(save: Boolean = true, action: () -> Unit): Boolean {
        Util.confirmDelete(requireContext()) {
            action()
            if (save) TreeUtil.save(true, person)
            refresh()
        }
        return true
    }

    private fun swapNames(direction: Int) {
        val index = person.names.indexOf(pieceObject)
        Collections.swap(person.names, index, index + direction)
    }

    private fun swapEvents(direction: Int) {
        val index = person.eventsFacts.indexOf(pieceObject)
        Collections.swap(person.eventsFacts, index, index + direction)
    }

    private fun swapSharedNotes(direction: Int) {
        Collections.swap(person.noteRefs, sharedNoteIndex, sharedNoteIndex + direction)
    }

    private fun swapNotes(direction: Int) {
        val index = person.notes.indexOf(pieceObject)
        Collections.swap(person.notes, index, index + direction)
    }

    private fun swapSourceCitations(direction: Int) {
        val index = person.sourceCitations.indexOf(pieceObject)
        Collections.swap(person.sourceCitations, index, index + direction)
    }
}
