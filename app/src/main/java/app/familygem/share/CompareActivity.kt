package app.familygem.share

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import app.familygem.BaseActivity
import app.familygem.Global
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Extra
import app.familygem.util.TreeUtils
import app.familygem.util.Utils
import app.familygem.util.getBasicData
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.Change
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.Submitter
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Activity that introduces the process for importing updates in an existing tree.
 * The updates are taken from a tree received on sharing.
 */
class CompareActivity : BaseActivity() {

    private var idTree1 = 0
    private var idTree2 = 0
    private lateinit var sharingDate: Date
    private lateinit var changeDateFormat: SimpleDateFormat

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.compare_activity)
        idTree1 = intent.getIntExtra(Extra.TREE_ID, 1) // Old tree present in the app
        idTree2 = intent.getIntExtra(Extra.TREE_ID_2, 1) // New tree received in sharing
        Global.treeId2 = idTree2 // It will be used by ProcessActivity and ConfirmationActivity
        lifecycleScope.launch(IO) {
            Global.gc = TreeUtils.openGedcomTemporarily(idTree1, true)
            Global.gc2 = TreeUtils.openGedcomTemporarily(idTree2, false)
            if (Global.gc == null || Global.gc2 == null) {
                Utils.toast(R.string.no_useful_data)
                onBackPressedDispatcher.onBackPressed()
                return@launch
            }
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome")) // Synchronizes all dates to the italian time zone
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)
            sharingDate = intent.getStringExtra(Extra.DATE_ID)?.let { dateFormat.parse(it) }!!
            changeDateFormat = SimpleDateFormat("d MMM yyyyHH:mm:ss", Locale.ENGLISH)
            Comparison.reset() // It's necessary for example after a device configuration change

            // Compares all the records of the two GEDCOMs
            for (obj2 in Global.gc2.families) compare(Global.gc.getFamily(obj2.id), obj2, 7)
            for (obj in Global.gc.families) reconcile(obj, Global.gc2.getFamily(obj.id), 7)
            for (obj2 in Global.gc2.people) compare(Global.gc.getPerson(obj2.id), obj2, 6)
            for (obj in Global.gc.people) reconcile(obj, Global.gc2.getPerson(obj.id), 6)
            for (obj2 in Global.gc2.sources) compare(Global.gc.getSource(obj2.id), obj2, 5)
            for (obj in Global.gc.sources) reconcile(obj, Global.gc2.getSource(obj.id), 5)
            for (obj2 in Global.gc2.media) compare(Global.gc.getMedia(obj2.id), obj2, 4)
            for (obj in Global.gc.media) reconcile(obj, Global.gc2.getMedia(obj.id), 4)
            for (obj2 in Global.gc2.repositories) compare(Global.gc.getRepository(obj2.id), obj2, 3)
            for (obj in Global.gc.repositories) reconcile(obj, Global.gc2.getRepository(obj.id), 3)
            for (obj2 in Global.gc2.submitters) compare(Global.gc.getSubmitter(obj2.id), obj2, 2)
            for (obj in Global.gc.submitters) reconcile(obj, Global.gc2.getSubmitter(obj.id), 2)
            for (obj2 in Global.gc2.notes) compare(Global.gc.getNote(obj2.id), obj2, 1)
            for (obj in Global.gc.notes) reconcile(obj, Global.gc2.getNote(obj.id), 1)

            withContext(Main) { setupInterface() }
        }
        onBackPressedDispatcher.addCallback(this) { onSupportNavigateUp() }
    }

    private fun setupInterface() {
        val tree2 = Global.settings.getTree(idTree2)
        if (Comparison.getList().isEmpty()) {
            setTitle(R.string.tree_without_news)
            if (tree2.grade != 30) {
                tree2.grade = 30
                Global.settings.save()
            }
        } else if (tree2.grade != 20) {
            tree2.grade = 20
            Global.settings.save()
        }
        populateCard(Global.gc, idTree1, R.id.compare_old)
        populateCard(Global.gc2, idTree2, R.id.compare_new)
        findViewById<TextView>(R.id.compare_description).text = getString(R.string.tree_news_imported, Comparison.getList().size)
        val button1 = findViewById<Button>(R.id.compare_button1)
        val button2 = findViewById<Button>(R.id.compare_button2)
        if (Comparison.getList().size > 0) {
            // 'Review singly' button
            button1.setOnClickListener {
                startActivity(Intent(this@CompareActivity, ProcessActivity::class.java).putExtra(Extra.POSITION, 1))
            }
            // 'Accept all' button
            button2.setOnClickListener { button ->
                button.isEnabled = false
                Comparison.get().numChoices = 0
                for (front in Comparison.getList()) {
                    if (front.canBothAddAndReplace) Comparison.get().numChoices++
                }
                val intent = Intent(this@CompareActivity, ProcessActivity::class.java)
                intent.putExtra(Extra.POSITION, 1)
                if (Comparison.get().numChoices > 0) {
                    AlertDialog.Builder(this) // Dialog requesting a revision
                            .setTitle(if (Comparison.get().numChoices == 1) getString(R.string.one_update_choice)
                            else getString(R.string.many_updates_choice, Comparison.get().numChoices))
                            .setMessage(R.string.updates_replace_add)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                Comparison.get().autoContinue = true
                                Comparison.get().choicesMade = 1
                                startActivity(intent)
                            }.setNeutralButton(android.R.string.cancel) { _, _ -> button2.isEnabled = true }
                            .setOnCancelListener { _ -> button2.isEnabled = true }.show()
                } else { // Continues automatically
                    Comparison.get().autoContinue = true
                    startActivity(intent)
                }
            }
        } else {
            button1.setText(R.string.delete_imported_tree)
            button1.setOnClickListener { _ ->
                TreeUtils.deleteTree(idTree2)
                onBackPressedDispatcher.onBackPressed()
            }
            button2.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        Comparison.reset() // Resets the Comparison singleton
        return super.onSupportNavigateUp()
    }

    override fun onRestart() {
        super.onRestart()
        findViewById<Button>(R.id.compare_button2).isEnabled = true // In case it's disabled
        Comparison.get().autoContinue = false // Resets it in case 'Accept all' was chosen
    }

    /**
     * Choose whether to add the two objects to the evaluation list.
     */
    private fun compare(obj: Any?, obj2: Any, type: Int) {
        val change = getChange(obj)
        val change2 = getChange(obj2)
        var modification = 0
        if (obj == null && isRecent(change2)) // obj2 has been added in the new tree -> ADD
            modification = 1 else {
            if (change == null && change2 != null) modification = 1 else if (change != null && change2 != null &&
                    !(change.dateTime.value == change2.dateTime.value && change.dateTime.time == change2.dateTime.time)) {
                if (isRecent(change) && isRecent(change2)) { // Both changed after sharing -> ADD / REPLACE
                    modification = 2
                } else if (isRecent(change2)) // Only obj2 has been changed -> REPLACE
                    modification = 1
            }
        }
        if (modification > 0) {
            val front = Comparison.addFront(obj, obj2, type)
            if (modification == 2) front.canBothAddAndReplace = true
        }
    }

    /**
     * The same for the remaining objects deleted in the old tree.
     */
    private fun reconcile(obj: Any, obj2: Any?, type: Int) {
        if (obj2 == null && !isRecent(getChange(obj))) Comparison.addFront(obj, null, type)
    }

    /**
     * Finds if a top-level record has been modified after the date of sharing.
     *
     * @param change Actual change date of the top-level record
     * @return true if the record is more recent than the date of sharing
     */
    private fun isRecent(change: Change?): Boolean {
        var itIs = false
        if (change != null && change.dateTime != null) {
            try { // TODO: test also with null Time
                var zoneId = U.castJsonString(change.getExtension("zone"))
                if (zoneId == null) zoneId = "UTC"
                val timeZone = TimeZone.getTimeZone(zoneId)
                changeDateFormat.timeZone = timeZone
                val recordDate = changeDateFormat.parse(change.dateTime.value + change.dateTime.time)
                if (recordDate != null) {
                    itIs = recordDate.after(sharingDate)
                    /* Timezone shift in hours
                    val hours = TimeUnit.MILLISECONDS.toHours(timeZone.getOffset(recordDate.time).toLong())
                    l(recordDate, "\t" + itIs + "\t" + if (hours > 0) "+" else "", hours, "\t" + timeZone.id)
                    */
                }
            } catch (_: ParseException) {
            }
        }
        return itIs
    }

    /**
     * Returns the Change date of any top-level object.
     */
    private fun getChange(obj: Any?): Change? {
        return when (obj) {
            is Family -> obj.change
            is Person -> obj.change
            is Source -> obj.change
            is Media -> obj.change
            is Repository -> obj.change
            is Submitter -> obj.change
            is Note -> obj.change
            else -> null
        }
    }

    private fun populateCard(gedcom: Gedcom, treeId: Int, cardId: Int) {
        val card = findViewById<CardView>(cardId)
        val tree = Global.settings.getTree(treeId)
        val title = card.findViewById<TextView>(R.id.compare_title)
        val data = card.findViewById<TextView>(R.id.compare_text)
        title.text = tree.title
        data.text = tree.getBasicData()
        if (cardId == R.id.compare_new) {
            if (tree.grade == 30) {
                card.setCardBackgroundColor(ResourcesCompat.getColor(resources, R.color.consumed, null))
                title.setTextColor(ResourcesCompat.getColor(resources, R.color.gray_text, null))
                data.setTextColor(ResourcesCompat.getColor(resources, R.color.gray_text, null))
            } else card.setCardBackgroundColor(ResourcesCompat.getColor(resources, R.color.accent_medium, null))
            val submitter = gedcom.getSubmitter(tree.shares[tree.shares.size - 1].submitter)
            var txt = StringBuilder()
            if (submitter != null) {
                var name = submitter.name
                if (name == null || name.isEmpty()) name = getString(android.R.string.unknownName)
                txt.append(getString(R.string.sent_by, name)).append("\n")
            }
            //if (Comparison.getList().size > 0) txt.append("Updates:\t");
            for (i in 7 downTo 1) txt.append(writeDifferences(i))
            if (txt.toString().endsWith("\n")) txt = StringBuilder(txt.substring(0, txt.length - 1))
            val subText = card.findViewById<TextView>(R.id.compare_subText)
            subText.text = txt.toString()
            subText.visibility = View.VISIBLE
        }
        card.findViewById<View>(R.id.compare_date).visibility = View.GONE // TODO: Header date
    }

    private var singulars = intArrayOf(R.string.shared_note, R.string.submitter, R.string.repository, R.string.shared_media, R.string.source, R.string.person, R.string.family)
    private var plurals = intArrayOf(R.string.shared_notes, R.string.submitters, R.string.repositories, R.string.shared_medias, R.string.sources, R.string.persons, R.string.families)

    private fun writeDifferences(type: Int): String {
        var type = type
        var changes = 0
        for (front in Comparison.getList()) {
            if (front.type == type) changes++
        }
        var text = ""
        if (changes > 0) {
            type-- // Zero-based index
            val description = if (changes == 1) singulars[type] else plurals[type]
            text = "\t\t+" + changes + " " + getString(description).lowercase(Locale.getDefault()) + "\n"
        }
        return text
    }
}
