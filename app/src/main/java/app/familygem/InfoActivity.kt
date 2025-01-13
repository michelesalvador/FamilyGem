package app.familygem

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import app.familygem.constant.Extra
import app.familygem.util.NoteUtil
import app.familygem.util.TreeUtil
import app.familygem.util.writeName
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.CharacterSet
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.GedcomVersion
import org.folg.gedcom.model.Generator
import java.io.File
import java.util.Locale

class InfoActivity : BaseActivity() {

    private var gedcom: Gedcom? = null
    private lateinit var table: TableLayout
    private val leftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.info_activity)
        val treeId = intent.getIntExtra(Extra.TREE_ID, 1)
        val tree = Global.settings.getTree(treeId)
        val file = File(filesDir, "$treeId.json")
        val titleView = findViewById<TextView>(R.id.info_textTitle)
        val textView = findViewById<TextView>(R.id.info_text)
        table = findViewById(R.id.info_table)
        row(getText(R.string.title), tree.title)
        if (Global.settings.expert) row(getText(R.string.id), tree.id.toString())
        if (!file.exists()) {
            titleView.text = getText(R.string.file)
            textView.text = "${getText(R.string.item_exists_but_file)}\n${file.absolutePath}"
        } else {
            if (Global.settings.expert) row(getText(R.string.file), file.absolutePath)
            lifecycleScope.launch(IO) {
                gedcom = TreeUtil.openGedcomTemporarily(treeId, false)
                if (gedcom == null) {
                    withContext(Main) {
                        val wrong = getString(R.string.something_wrong)
                        titleView.text = wrong.substring(0, wrong.length - 1) // Removes final dot
                        textView.text = getString(R.string.no_useful_data)
                    }
                } else {
                    TreeUtil.refreshData(gedcom!!, tree)
                    row2(getText(R.string.persons), tree.persons.toString())
                    row2(getText(R.string.families), gedcom!!.families.size.toString())
                    row2(getText(R.string.media), tree.media.toString())
                    row2(getText(R.string.sources), gedcom!!.sources.size.toString())
                    row2(getText(R.string.repositories), gedcom!!.repositories.size.toString())
                    row2(getText(R.string.generations), tree.generations.toString())
                    if (tree.root != null) {
                        row2(getText(R.string.root), U.properName(gedcom!!.getPerson(tree.root)))
                    }
                    var shareList = ""
                    if (tree.shares != null && tree.shares.isNotEmpty()) {
                        for (share in tree.shares) {
                            shareList += getDateFromDateId(share.dateId)
                            if (gedcom!!.getSubmitter(share.submitter) != null)
                                shareList += " - ${gedcom!!.getSubmitter(share.submitter).writeName()}\n"
                        }
                    }
                    withContext(Main) {
                        if (shareList.isEmpty()) {
                            titleView.visibility = View.GONE
                            textView.visibility = View.GONE
                        } else {
                            titleView.text = getText(R.string.shares)
                            textView.text = shareList
                        }
                        displayHeader(treeId, file)
                    }
                }
            }
        }
    }

    private fun row(title: CharSequence, text: String?) {
        if (text != null) {
            val row = TableRow(this)
            val cell1 = TextView(this)
            cell1.setTypeface(null, Typeface.BOLD)
            if (leftToRight) cell1.setPadding(0, 0, U.dpToPx(5F), 0) else cell1.setPadding(U.dpToPx(5F), 0, 0, 0)
            cell1.gravity = if (leftToRight) Gravity.RIGHT else Gravity.LEFT // START/END don't work as expected on RTL layout
            cell1.text = title
            row.addView(cell1)
            val cell2 = TextView(this)
            cell2.gravity = if (leftToRight) Gravity.LEFT else Gravity.RIGHT
            cell2.text = text
            row.addView(cell2)
            table.addView(row)
            rowPlaced = true // Used by header table
        }
    }

    private suspend fun row2(title: CharSequence, text: String) {
        withContext(Main) { row(title, text) }
    }

    private fun displayHeader(treeId: Int, file: File) {
        val headerButton = findViewById<Button>(R.id.info_headerButton)
        val layout = findViewById<LinearLayout>(R.id.info_layout)
        val h = gedcom!!.header
        if (h == null) {
            headerButton.setText(R.string.create_header)
            headerButton.setOnClickListener {
                gedcom!!.header = TreeUtil.createHeader(file.name)
                GlobalScope.launch(IO) {
                    TreeUtil.saveJson(gedcom!!, treeId)
                    withContext(Main) { recreate() }
                }
            }
        } else {
            findViewById<View>(R.id.info_header).visibility = View.VISIBLE
            table = findViewById(R.id.info_headerTable)
            if (h.file != null) row(getText(R.string.file), h.file)
            if (h.characterSet != null) {
                row(getText(R.string.characrter_set), h.characterSet.value)
                row(getText(R.string.version), h.characterSet.version)
            }
            line()
            row(getText(R.string.language), h.language)
            line()
            row(getText(R.string.copyright), h.copyright)
            line()
            if (h.generator != null) {
                row(getText(R.string.software), if (h.generator.name != null) h.generator.name else h.generator.value)
                row(getText(R.string.version), h.generator.version)
                if (h.generator.generatorCorporation != null) {
                    row(getText(R.string.corporation), h.generator.generatorCorporation.value)
                    if (h.generator.generatorCorporation.address != null)
                        row(getText(R.string.address), h.generator.generatorCorporation.address.displayValue) // non è male
                    row(getText(R.string.telephone), h.generator.generatorCorporation.phone)
                    row(getText(R.string.fax), h.generator.generatorCorporation.fax)
                }
                line()
                if (h.generator.generatorData != null) {
                    row(getText(R.string.source), h.generator.generatorData.value)
                    row(getText(R.string.date), h.generator.generatorData.date)
                    row(getText(R.string.copyright), h.generator.generatorData.copyright)
                }
            }
            line()
            if (h.getSubmitter(gedcom) != null) row(getText(R.string.submitter), h.getSubmitter(gedcom).writeName()) // TODO: clickable?
            if (gedcom!!.submission != null) row(getText(R.string.submission), gedcom!!.submission.description)
            line()
            if (h.gedcomVersion != null) {
                row(getText(R.string.gedcom), h.gedcomVersion.version)
                row(getText(R.string.form), h.gedcomVersion.form)
            }
            row(getText(R.string.destination), h.destination)
            line()
            if (h.dateTime != null) {
                row(getText(R.string.date), h.dateTime.value)
                row(getText(R.string.time), h.dateTime.time)
            }
            line()
            // Header extensions
            for (extension in U.findExtensions(h)) {
                row(extension.name, extension.text)
            }
            line()
            if (lastLine != null) table.removeView(lastLine)

            // Button to update the GEDCOM header with the Family Gem parameters
            headerButton.setOnClickListener {
                it.isEnabled = false
                h.file = "$treeId.json"
                var characterSet = h.characterSet
                if (characterSet == null) {
                    characterSet = CharacterSet()
                    h.characterSet = characterSet
                }
                characterSet.value = "UTF-8"
                characterSet.version = null
                val locale = Locale(Locale.getDefault().language)
                h.language = locale.getDisplayLanguage(Locale.ENGLISH)
                var program = h.generator
                if (program == null) {
                    program = Generator()
                    h.generator = program
                }
                program.value = "FAMILY_GEM"
                program.name = getString(R.string.app_name)
                //program.version = BuildConfig.VERSION_NAME // saveJson() will do that
                program.generatorCorporation = null
                var gedcomVersion = h.gedcomVersion
                if (gedcomVersion == null) {
                    gedcomVersion = GedcomVersion()
                    h.gedcomVersion = gedcomVersion
                }
                gedcomVersion.version = "5.5.1"
                gedcomVersion.form = "LINEAGE-LINKED"
                h.destination = null
                GlobalScope.launch(IO) {
                    TreeUtil.saveJson(gedcom!!, treeId)
                    withContext(Main) { recreate() }
                }
            }
            NoteUtil.placeNotes(layout, h, false, gedcom!!) // TODO: improve note managing: detailed = true etc.
        }
        headerButton.visibility = View.VISIBLE
        // GEDCOM extensions: zero level non-standard tags
        for (extension in U.findExtensions(gedcom)) {
            U.place(layout, extension.name, extension.text)
        }
    }

    private fun getDateFromDateId(id: String?): String {
        return if (id == null) ""
        else (id.substring(0, 4) + "-" + id.substring(4, 6) + "-" + id.substring(6, 8) + " "
                + id.substring(8, 10) + ":" + id.substring(10, 12) + ":" + id.substring(12))
    }

    private var rowPlaced = false // Prevents from putting more than one consecutive line()
    private var lastLine: TableRow? = null

    private fun line() {
        if (rowPlaced) {
            lastLine = TableRow(this)
            val cell = View(this)
            cell.setBackgroundResource(R.color.primary)
            lastLine!!.addView(cell)
            val param = cell.layoutParams as TableRow.LayoutParams
            param.weight = 1F
            param.span = 2
            param.height = 1
            param.topMargin = U.dpToPx(3F)
            param.bottomMargin = U.dpToPx(3F)
            cell.layoutParams = param
            table.addView(lastLine)
            rowPlaced = false
        }
    }
}
