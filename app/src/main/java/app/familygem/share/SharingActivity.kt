package app.familygem.share

import android.content.Intent
import android.net.Uri
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
import app.familygem.Exporter
import app.familygem.Global
import app.familygem.R
import app.familygem.Settings
import app.familygem.Settings.Share
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.databinding.SharingActivityBinding
import app.familygem.main.MainActivity
import app.familygem.main.SubmittersFragment
import app.familygem.util.ChangeUtil.actualDateTime
import app.familygem.util.ChangeUtil.updateChangeDate
import app.familygem.util.PersonUtil
import app.familygem.util.TreeUtil
import app.familygem.util.TreeUtil.createHeader
import app.familygem.util.Util
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.source
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Submitter
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min

/** Allows to share a tree by uploading it to the online server. */
class SharingActivity : BaseActivity(R.string.share_tree) {

    private var gedcom: Gedcom? = null
    private lateinit var tree: Settings.Tree
    private lateinit var exporter: Exporter
    private lateinit var titleView: EditText
    private var rootView: View? = null // The person root of the tree
    private lateinit var submitterName: String
    private lateinit var submitterId: String
    private var accessible = false
    private lateinit var apiToken: String
    private var dateId: String? = null
    private var successfulUpload = false // To avoid uploading twice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent(SharingActivityBinding.inflate(layoutInflater).root)
        val treeId = intent.getIntExtra(Extra.TREE_ID, 1)
        tree = Global.settings.getTree(treeId)
        titleView = findViewById(R.id.share_title)
        titleView.setText(tree.title)
        if (tree.grade == 10) findViewById<TextView>(R.id.share_submitter_title).setText(R.string.changes_submitter)
        exporter = Exporter(this, progressView)
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
                progressView.visibility = View.VISIBLE

                // Tree title
                val editedTitle = titleView.text.trim().toString()
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
                val editedSubmitterName = submitterView.text.trim().toString()
                if (editedSubmitterName != submitterName) {
                    submitterName = editedSubmitterName
                    submitter!!.name = submitterName
                    updateChangeDate(submitter)
                }
                submitterId = submitter!!.id
                GlobalScope.launch(IO) {
                    TreeUtil.saveJson(gedcom!!, tree.id) // Possibly bypassing the preference not to save automatically
                }

                // Tree accessibility for app developer
                val accessibleTree = findViewById<CheckBox>(R.id.share_allow)
                accessible = accessibleTree.isChecked

                // Sends
                lifecycleScope.launch(IO) {
                    sendShareData()
                        .onSuccess {
                            successfulUpload = true
                            Util.toast(R.string.correctly_uploaded)
                            restore()
                            concludeShare()
                        }.onFailure {
                            it.printStackTrace()
                            Util.toast(it.message)
                            restore()
                        }
                }
            }
        }
    }

    /** Places a small person card that represents the root of the shared tree. */
    private fun displayShareRoot() {
        val rootId = if (gedcom!!.getPerson(tree.shareRoot) != null) tree.shareRoot
        else if (gedcom!!.getPerson(tree.root) != null) tree.root
        else TreeUtil.findRootId(gedcom!!) // Root is missing or tree has no people
        tree.shareRoot = rootId // In case it was different
        val person = gedcom!!.getPerson(rootId)
        if (person != null && tree.grade < 10) { // Shown only at first sharing, not coming back
            val rootLayout = findViewById<LinearLayout>(R.id.share_root)
            rootLayout.visibility = View.VISIBLE
            rootLayout.removeView(rootView)
            rootView = PersonUtil.placeSmallPerson(rootLayout, person)
            rootView!!.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra(Choice.PERSON, true)
                startActivityForResult(intent, 5007)
            }
        }
    }

    /** Finds the first non-passed submitter. */
    private fun getFreshSubmitter(): Submitter? {
        for (submitter in gedcom!!.submitters) {
            if (submitter.getExtension("passed") == null) return submitter
        }
        return null
    }

    /** Verifies that a EditText is filled in. */
    private fun checkCompiled(field: EditText, message: Int): Boolean {
        if (field.text.isBlank()) {
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
    private suspend fun sendShareData(): Result<Boolean> {
        return try {
            apiToken = Util.getToken()
            val jsonObject = JSONObject().apply {
                put("title", tree.title)
                put("submitterName", submitterName)
                put("accessible", accessible)
            }
            val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(Global.apiUrl + "trees")
                .header("Authorization", "Bearer $apiToken")
                .post(requestBody).build()
            Global.okHttpClient.newCall(request).execute().use { response ->
                val json = JSONObject(response.body.string())
                if (response.isSuccessful) {
                    dateId = json.getString("dateId")
                    val share = Share(dateId, submitterId)
                    tree.addShare(share)
                    Global.settings.save()
                    val treeFile = File(cacheDir, "$dateId.zip")
                    if (exporter.exportZipBackup(tree.shareRoot, 9, Uri.fromFile(treeFile))) {
                        uploadChunkedTree(treeFile)
                        Result.success(true)
                    } else {
                        throw Exception(exporter.errorMessage)
                    }
                } else {
                    throw Exception(json.optString("detail", response.message))
                }
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /** Uploads the tree ZIP file chunked in parts. */
    private suspend fun uploadChunkedTree(zipFile: File) {
        val totalSize = zipFile.length()
        progressView.displayBar("Uploading tree", totalSize)
        val chunkSize = (2 * 1024 * 1024).toLong() // 2 MB
        val totalChunks = ceil(totalSize.toDouble() / chunkSize).toLong()
        var totalBytesWritten = 0L
        var totalBytesUploaded = 0L
        val maxRetries = 3
        val uploadClient = Global.okHttpClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true).build()
        val deferred = (0 until totalChunks).map { i ->
            lifecycleScope.async(IO) {
                val offset = i * chunkSize
                val currentChunkSize = min(chunkSize, totalSize - offset)
                var retryCount = 0
                var success = false
                while (!success) {
                    try {
                        val requestBody = object : RequestBody() {
                            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                            override fun contentLength() = currentChunkSize
                            override fun writeTo(sink: okio.BufferedSink) {
                                zipFile.source().buffer().use { source ->
                                    source.skip(offset)
                                    val buffer = ByteArray(8192) // 8 kB segments
                                    var bytesInChunkWritten = 0L
                                    while (bytesInChunkWritten < currentChunkSize) {
                                        val toRead = min(buffer.size.toLong(), currentChunkSize - bytesInChunkWritten).toInt()
                                        val read = source.read(buffer, 0, toRead)
                                        if (read == -1) break
                                        sink.write(buffer, 0, read)
                                        sink.flush()
                                        bytesInChunkWritten += read
                                        totalBytesWritten += read
                                        progressView.progress = totalBytesWritten
                                    }
                                }
                            }
                        }
                        val request = Request.Builder().url(Global.apiUrl + "files?dateId=$dateId&offset=$offset")
                            .header("Authorization", "Bearer $apiToken")
                            .post(requestBody).build()
                        uploadClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                totalBytesUploaded += currentChunkSize
                                success = true
                                if (totalBytesUploaded >= totalSize) {
                                    progressView.hideBar()
                                }
                            } else {
                                val jsonResponse = JSONObject(response.body.string())
                                throw Exception(jsonResponse.optString("detail", response.message))
                            }
                        }
                    } catch (exception: Exception) {
                        retryCount++
                        if (retryCount >= maxRetries) {
                            progressView.hideBar()
                            throw exception
                        }
                        Thread.sleep(1000L * retryCount) // Exponential backoff
                    }
                }
            }
        }
        deferred.awaitAll()
    }

    /** Displays available apps to share the link. */
    private fun concludeShare() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sharing_tree))
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.click_this_link, "https://www.familygem.app/share.php?tree=$dateId"))
        /* Tornando indietro da una app di messaggistica il requestCode 35417 arriva sempre corretto
            Invece il resultCode può essere RESULT_OK o RESULT_CANCELED a capocchia
            Ad esempio da Gmail ritorna indietro sempre con RESULT_CANCELED sia che l'email è stata inviata o no
            anche inviando un Sms ritorna RESULT_CANCELED anche se l'sms è stato inviato
            oppure da Whatsapp è RESULT_OK sia che il messaggio è stato inviato o no
            In pratica non c'è modo di sapere se nella app di messaggistica il messaggio è stato inviato. */
        startActivityForResult(Intent.createChooser(intent, getText(R.string.share_with)), 35417)
    }

    private suspend fun restore() {
        withContext(Main) {
            findViewById<View>(R.id.share_button).isEnabled = true
            progressView.visibility = View.GONE
        }
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
