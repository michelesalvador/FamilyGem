package app.familygem.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import app.familygem.BaseActivity
import app.familygem.BuildConfig
import app.familygem.Exporter
import app.familygem.Global
import app.familygem.Principal
import app.familygem.R
import app.familygem.Settings
import app.familygem.Settings.Share
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.constant.Json
import app.familygem.list.SubmittersFragment
import app.familygem.util.ChangeUtils.actualDateTime
import app.familygem.util.ChangeUtils.updateChangeDate
import app.familygem.util.TreeUtils
import app.familygem.util.TreeUtils.createHeader
import app.familygem.util.Utils
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Submitter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Allows to share a tree by uploading it to the online server.
 */
class SharingActivity : BaseActivity() {

    private var gedcom: Gedcom? = null
    private lateinit var tree: Settings.Tree
    private lateinit var exporter: Exporter
    private lateinit var titleView: EditText
    private var rootView: View? = null // The person root of the tree
    private lateinit var submitterName: String
    private lateinit var submitterId: String
    private var accessible = 0 // 0 = false, 1 = true
    private var dateId: String? = null
    private var successfulUpload = false // To avoid uploading twice

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.sharing_activity)
        val treeId = intent.getIntExtra(Extra.TREE_ID, 1)
        tree = Global.settings.getTree(treeId)
        titleView = findViewById(R.id.share_title)
        titleView.setText(tree.title)
        if (tree.grade == 10) findViewById<TextView>(R.id.share_submitter_title).setText(R.string.changes_submitter)
        exporter = Exporter(this)
        lifecycleScope.launch(IO) {
            exporter.openTree(treeId)
            gedcom = Global.gc
            withContext(Main) {
                if (gedcom != null) setupInterface()
                else findViewById<View>(R.id.share_layout).visibility = View.GONE
            }
        }
    }

    private fun setupInterface() {
        displayShareRoot()
        // Submitter name
        var submitter: Submitter? = null
        val freshSubmitter = getFreshSubmitter()
        // Tree in Italy with submitter selected as main
        if (tree.grade == 0 && gedcom!!.header != null && gedcom!!.header.getSubmitter(gedcom) != null)
            submitter = gedcom!!.header.getSubmitter(gedcom)
        // In Italy there are submitters but no main one: takes the last one
        else if (tree.grade == 0 && gedcom!!.submitters.isNotEmpty())
            submitter = gedcom!!.submitters[gedcom!!.submitters.size - 1]
        // In Australia there are fresh submitters: takes one
        else if (tree.grade == 10 && freshSubmitter != null)
            submitter = freshSubmitter
        val submitterView = findViewById<EditText>(R.id.share_submitter)
        submitterName = if (submitter == null) "" else submitter.name
        submitterView.setText(submitterName)

        // Displays an alert for the acknowledgment of sharing
        if (!Global.settings.shareAgreement) {
            AlertDialog.Builder(this).setTitle(R.string.share_sensitive)
                    .setMessage(R.string.aware_upload_server)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Global.settings.shareAgreement = true
                        Global.settings.save()
                    }.setNeutralButton(R.string.remind_later, null).show()
        }

        // Collects sharing data and posts to database
        findViewById<Button>(R.id.share_button).setOnClickListener { button: View ->
            if (successfulUpload) { // Tree already uploaded: we only share the link
                concludeShare()
            } else {
                if (checkCompiled(titleView, R.string.please_title) || checkCompiled(submitterView, R.string.please_name))
                    return@setOnClickListener

                button.isEnabled = false
                findViewById<View>(R.id.share_wheel).visibility = View.VISIBLE

                // Tree title
                val editedTitle = titleView.text.toString()
                if (tree.title != editedTitle) {
                    tree.title = editedTitle
                    Global.settings.save()
                }

                // Submitter update
                var header = gedcom!!.header
                if (header == null) {
                    header = createHeader(tree.id.toString() + ".json")
                    gedcom!!.header = header
                } else header.dateTime = actualDateTime()
                if (submitter == null) {
                    submitter = SubmittersFragment.createSubmitter(null)
                }
                if (header.submitterRef == null) {
                    header.submitterRef = submitter!!.id
                }
                val editedSubmitterName = submitterView.text.toString()
                if (editedSubmitterName != submitterName) {
                    submitterName = editedSubmitterName
                    submitter!!.name = submitterName
                    updateChangeDate(submitter)
                }
                submitterId = submitter!!.id
                GlobalScope.launch(IO) {
                    TreeUtils.saveJson(gedcom!!, tree.id) // Possibly bypassing the preference not to save automatically
                }

                // Tree accessibility for app developer
                val accessibleTree = findViewById<CheckBox>(R.id.share_allow)
                accessible = if (accessibleTree.isChecked) 1 else 0

                // Sends
                if (BuildConfig.PASS_KEY.isNotEmpty()) {
                    lifecycleScope.launch(Default) { sendShareData() }
                } else {
                    restore()
                    Toast.makeText(this, R.string.something_wrong, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Places a small person card that represents the root of the shared tree.
     */
    private fun displayShareRoot() {
        val rootId: String
        if (tree.shareRoot != null && gedcom!!.getPerson(tree.shareRoot) != null) {
            rootId = tree.shareRoot
        } else if (tree.root != null && gedcom!!.getPerson(tree.root) != null) {
            rootId = tree.root
            tree.shareRoot = rootId // To immediately share the tree without changing the root
        } else {
            rootId = U.findRootId(gedcom)
            tree.shareRoot = rootId
        }
        val person = gedcom!!.getPerson(rootId)
        if (person != null && tree.grade < 10) { // Shown only at first sharing, not coming back
            val rootLayout = findViewById<LinearLayout>(R.id.share_root)
            rootLayout.visibility = View.VISIBLE
            rootLayout.removeView(rootView)
            rootView = U.placeSmallPerson(rootLayout, person)
            rootView!!.setOnClickListener {
                val intent = Intent(this, Principal::class.java)
                intent.putExtra(Choice.PERSON, true)
                startActivityForResult(intent, 5007)
            }
        }
    }

    /**
     * Finds the first non-passed submitter.
     */
    private fun getFreshSubmitter(): Submitter? {
        for (submitter in gedcom!!.submitters) {
            if (submitter.getExtension("passed") == null) return submitter
        }
        return null
    }

    /**
     * Verify that a EditText is filled in.
     */
    private fun checkCompiled(field: EditText, message: Int): Boolean {
        if (field.text.isEmpty()) {
            field.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return true
        }
        return false
    }

    /**
     * Inserts the share summary in the database of www.familygem.app.
     * If all goes well creates the ZIP file with the tree and images.
     */
    private suspend fun sendShareData() {
        try {
            var protocol = "https"
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) protocol = "http"
            val url = URL("$protocol://www.familygem.app/insert_share.php")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            val stream: OutputStream = BufferedOutputStream(connection.outputStream)
            val writer = BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8))
            val query = "passKey=" + URLEncoder.encode(BuildConfig.PASS_KEY, "UTF-8") +
                    "&treeTitle=" + URLEncoder.encode(tree.title, "UTF-8") +
                    "&submitterName=" + URLEncoder.encode(submitterName, "UTF-8") +
                    "&accessible=" + accessible
            writer.write(query)
            writer.flush()
            writer.close()
            stream.close()

            // Answer
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val line = reader.readLine()
            reader.close()
            connection.disconnect()
            if (line.startsWith("20")) {
                dateId = line.replace("[-: ]".toRegex(), "")
                val share = Share(dateId, submitterId)
                tree.addShare(share)
                Global.settings.save()
                val treeFile = File(cacheDir, "$dateId.zip")
                if (exporter.exportZipBackup(tree.shareRoot, 9, Uri.fromFile(treeFile))) {
                    ftpUpload()
                } else throw Exception(exporter.errorMessage)
            } else throw Exception(line)
        } catch (exception: Exception) {
            Utils.toast(exception.localizedMessage)
            restoreSuspended()
        }
    }

    /**
     * Uploads via FTP the ZIP file with the shared tree.
     */
    private suspend fun ftpUpload() {
        val credential = U.getCredential(Json.FTP)
        if (credential != null) {
            try {
                val ftpClient = FTPClient()
                ftpClient.connect(credential.getString(Json.HOST), credential.getInt(Json.PORT))
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(credential.getString(Json.USER), credential.getString(Json.PASSWORD))
                ftpClient.changeWorkingDirectory(credential.getString(Json.SHARED_PATH))
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                val bufferedInput: BufferedInputStream
                val nomeZip = "$dateId.zip"
                bufferedInput = BufferedInputStream(FileInputStream("$cacheDir/$nomeZip"))
                successfulUpload = ftpClient.storeFile(nomeZip, bufferedInput)
                bufferedInput.close()
                ftpClient.logout()
                ftpClient.disconnect()
                if (successfulUpload) {
                    Utils.toast(R.string.correctly_uploaded)
                    concludeShare()
                } else throw Exception(getString(R.string.something_wrong))
            } catch (exception: Exception) {
                exception.printStackTrace()
                Utils.toast(exception.localizedMessage)
            }
        } else Utils.toast(R.string.something_wrong)
        restoreSuspended()
    }

    /**
     * Displays available apps to share the link.
     */
    private fun concludeShare() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sharing_tree))
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.click_this_link,
                "https://www.familygem.app/share.php?tree=$dateId"))
        /* Tornando indietro da una app di messaggistica il requestCode 35417 arriva sempre corretto
            Invece il resultCode può essere RESULT_OK o RESULT_CANCELED a capocchia
            Ad esempio da Gmail ritorna indietro sempre con RESULT_CANCELED sia che l'email è stata inviata o no
            anche inviando un Sms ritorna RESULT_CANCELED anche se l'sms è stato inviato
            oppure da Whatsapp è RESULT_OK sia che il messaggio è stato inviato o no
            In pratica non c'è modo di sapere se nella app di messaggistica il messaggio è stato inviato. */
        startActivityForResult(Intent.createChooser(intent, getText(R.string.share_with)), 35417)
    }

    private fun restore() {
        findViewById<View>(R.id.share_button).isEnabled = true
        findViewById<View>(R.id.share_wheel).visibility = View.GONE
    }

    private suspend fun restoreSuspended() {
        withContext(Main) { restore() }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Updates preferences to show the new root person chosen in PersonsFragment
        if (resultCode == RESULT_OK && requestCode == 5007) {
            tree.shareRoot = data!!.getStringExtra(Extra.RELATIVE_ID)
            Global.settings.save()
            displayShareRoot()
        }
        // Coming back from any messaging app, where the message was sent or not
        if (requestCode == 35417) {
            // TODO: close keyboard
            Toast.makeText(this, R.string.sharing_completed, Toast.LENGTH_LONG).show()
        }
    }
}
