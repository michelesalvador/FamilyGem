package app.familygem

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import app.familygem.Global.context
import app.familygem.constant.Extra
import app.familygem.constant.Type
import app.familygem.util.FileUtil
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import app.familygem.visitor.MediaLeaders
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.github.panpf.zoomimage.GlideZoomImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.NoteContainer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Activity to display and manage any file. */
class FileActivity : AppCompatActivity() {

    private lateinit var fileUri: FileUri
    private lateinit var wheel: ProgressBar
    private lateinit var treeDir: File
    private var type: Type = Type.NONE
    private var name = ""
    private lateinit var mediaList: List<MediaLeaders.MediaWrapper> // All media before copying or renaming one of them

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.file_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Displays back arrow on toolbar
        wheel = findViewById(R.id.progress_wheel)
        treeDir = getExternalFilesDir(Global.settings.openTree.toString())!!
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getSerializableExtra(Extra.TYPE, Type::class.java)!!
        else intent.getSerializableExtra(Extra.TYPE) as Type
        displayImage()
    }

    /** Displays the image as a zoomable view. */
    private fun displayImage() {
        wheel.visibility = View.VISIBLE
        val media = Global.editedMedia
        fileUri = FileUri(this, media)
        val resource: Any = if (fileUri.exists()) {
            name = fileUri.name!!
            when (type) {
                Type.DOCUMENT -> FileUtil.generateIcon(this, fileUri)
                Type.PDF -> FileUtil.previewPdf(this, fileUri).getOrElse { R.drawable.image }
                else -> fileUri.file ?: fileUri.uri!!
            }
        } else if (type == Type.WEB_ANYTHING) {
            name = URLUtil.guessFileName(media.file, null, null)
            if (name.endsWith(".bin")) name = name.substring(0, name.lastIndexOf(".bin")) // File extension added by URLUtil
            FileUtil.generateIcon(this, fileUri)
        } else if (type == Type.WEB_IMAGE) {
            name = URLUtil.guessFileName(media.file, null, null)
            media.file
        } else {
            name = ""
            R.drawable.image
        }
        title = name
        val builder = Glide.with(this).load(resource).placeholder(R.drawable.image).error(R.drawable.image)
            .listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    wheel.visibility = View.GONE
                    return false
                }

                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    wheel.visibility = View.GONE
                    Toast.makeText(this@FileActivity, e?.localizedMessage, Toast.LENGTH_LONG).show()
                    return false
                }
            })
        if (fileUri.exists()) {
            if (Global.croppedPaths.contains(fileUri.path)) {
                builder.signature(ObjectKey(Global.croppedPaths[fileUri.path]!!)) // Clears Glide cache
            }
        }
        val zoomImage = findViewById<GlideZoomImageView>(R.id.file_zoomImage)
        builder.into(zoomImage)
        zoomImage.setOnClickListener { // Opens the file with some other app
            val dataUri = if (fileUri.file != null) FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", fileUri.file!!)
            else if (type == Type.WEB_IMAGE || type == Type.WEB_ANYTHING) Global.editedMedia.file.toUri()
            else fileUri.uri!!
            val mimeType = contentResolver.getType(dataUri)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(dataUri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Necessary for folders owned by the app (provider)
            startActivity(Intent.createChooser(intent, null))
        }
    }

    /** Redraws image after cropping. */
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            displayImage()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 0, 0, R.string.info)
        if (fileUri.file == null || !fileUri.file!!.startsWith(treeDir)) menu.add(0, 1, 0, R.string.copy_app_storage)
        if (name.isNotEmpty() && type != Type.WEB_IMAGE && type != Type.WEB_ANYTHING) menu.add(0, 2, 0, R.string.rename)
        if (type == Type.CROPPABLE) menu.add(0, 3, 0, R.string.crop)
        if (type != Type.WEB_IMAGE && type != Type.WEB_ANYTHING) menu.add(0, 4, 0, R.string.delete)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0 -> displayFileInfo()
            1 -> copyToTreeStorage()
            2 -> renameFile()
            3 -> FileUtil.cropImage(this, fileUri.file, fileUri.uri, cropLauncher)
            4 -> Util.confirmDelete(this) { // Delete file
                val deleted = fileUri.delete()
                Toast.makeText(this, if (deleted) "File deleted." else "Unable to delete file.", Toast.LENGTH_LONG).show()
                if (deleted) {
                    Global.edited = true
                    finish()
                }
            }
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
        }
        return false
    }

    /** Displays basic info about the file. */
    private fun displayFileInfo() {
        try {
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(R.layout.file_info_dialog)
            val urlView = dialog.findViewById<TextView>(R.id.fileInfo_url)
            val sizeView = dialog.findViewById<TextView>(R.id.fileInfo_size)
            if (fileUri.exists()) {
                urlView?.text = if (Global.settings.expert) fileUri.path else fileUri.name
                if (type == Type.CROPPABLE) {
                    // Finds image dimensions in pixels
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    fileUri.fileDescriptor?.use { descriptor ->
                        BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                        dialog.findViewById<TextView>(R.id.fileInfo_dimensions)?.apply {
                            text = "${options.outWidth} × ${options.outHeight} px"
                            visibility = View.VISIBLE
                        }
                    }
                }
                val bytes = fileUri.file?.length() ?: DocumentFile.fromSingleUri(this, fileUri.uri!!)?.length() ?: 0
                sizeView?.text = Formatter.formatFileSize(this, bytes)
            } else if (type == Type.WEB_IMAGE || type == Type.WEB_ANYTHING) {
                urlView?.text = Global.editedMedia.file
                lifecycleScope.launch(IO) {
                    val connection = URL(Global.editedMedia.file).openConnection() as HttpURLConnection
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val bytes = connection.contentLength.toLong()
                        withContext(Main) {
                            sizeView?.text = if (bytes >= 0) Formatter.formatFileSize(this@FileActivity, bytes) else "Size unknown"
                        }
                    } else withContext(Main) {
                        sizeView?.text = "${connection.responseCode} ${connection.responseMessage}"
                    }
                }
            }
            urlView?.setOnLongClickListener {
                U.copyToClipboard(getText(android.R.string.copyUrl), urlView.text)
                Toast.makeText(this, "URL copied.", Toast.LENGTH_SHORT).show()
                true
            }
            dialog.show()
        } catch (exception: Exception) {
            Toast.makeText(this, exception.message ?: "Unable to display file info.", Toast.LENGTH_LONG).show()
        }
    }

    /** Copies the file to app external storage. */
    private fun copyToTreeStorage() {
        wheel.visibility = View.VISIBLE
        lifecycleScope.launch(IO) {
            setMediaList()
            val newFile = FileUtil.nextAvailableFileName(treeDir, name)
            if (fileUri.file != null) {
                fileUri.file!!.copyTo(newFile)
                completeCopy(newFile)
            } else if (fileUri.uri != null) {
                contentResolver.openInputStream(fileUri.uri!!).use { inputStream ->
                    newFile.outputStream().use {
                        inputStream?.copyTo(it)
                        completeCopy(newFile)
                    }
                }
            } else if (type == Type.WEB_IMAGE || type == Type.WEB_ANYTHING) {
                val connection = URL(Global.editedMedia.file).openConnection() as HttpURLConnection
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        newFile.outputStream().use {
                            inputStream?.copyTo(it)
                            type = if (type == Type.WEB_IMAGE) Type.CROPPABLE else Type.DOCUMENT
                            completeCopy(newFile)
                        }
                    }
                } else completeCopy()
            } else completeCopy()
        }
    }

    private suspend fun completeCopy(newFile: File? = null) {
        val message = if (newFile != null) {
            val modified = updateFileOccurrences(newFile.name, false)
            if (modified.isNotEmpty()) {
                TreeUtil.save(true, *modified)
            }
            withContext(Main) {
                displayImage()
                invalidateOptionsMenu() // To remove copy option
            }
            "File copied."
        } else "Unable to copy file."
        withContext(Main) {
            Toast.makeText(this@FileActivity, message, Toast.LENGTH_LONG).show()
            wheel.visibility = View.GONE
        }
    }

    /** Displays a dialog to edit the file name. */
    private fun renameFile() {
        val view = layoutInflater.inflate(R.layout.name_editor, null)
        val inputField = view.findViewById<EditText>(R.id.editName_input)
        val oldName = name
        inputField.setText(oldName)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.rename).setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                try {
                    wheel.visibility = View.VISIBLE
                    lifecycleScope.launch(IO) {
                        setMediaList()
                        val newName = inputField.text.toString().trim()
                        if (newName == oldName) return@launch
                        if (fileUri.rename(newName)) {
                            val modified = updateFileOccurrences(newName)
                            TreeUtil.save(true, *modified)
                            withContext(Main) { displayImage() }
                        } else throw Exception()
                    }
                } catch (exception: Exception) {
                    Toast.makeText(this, exception.message ?: "Unable to rename file.", Toast.LENGTH_LONG).show()
                }
            }.setNeutralButton(R.string.cancel, null).show()
        // Focus on input
        view.postDelayed({
            inputField.requestFocus()
            val dotIndex = inputField.text.indexOfLast { it == '.' }
            inputField.setSelection(0, if (dotIndex > 0) dotIndex else inputField.text.length)
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
        }, 300)
        // New name validation
        val filenames = if (fileUri.file != null) {
            fileUri.file!!.parentFile!!.list()!!.map { it.lowercase() }
        } else if (fileUri.uri != null) {
            val folderUri = Global.settings.currentTree.uris.first { fileUri.uri!!.toString().startsWith(it) }
            DocumentFile.fromTreeUri(context, folderUri.toUri())!!.listFiles().map { it.name!!.lowercase() }
        } else {
            emptyList()
        }
        val inputLayout = view.findViewById<TextInputLayout>(R.id.editName_layout)
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(sequence: CharSequence, start: Int, before: Int, count: Int) {
                var error: String? = null
                val proposal = sequence.toString().trim().lowercase()
                if (filenames.contains(proposal))
                    error = getString(R.string.existing_name)
                else if (proposal.isEmpty() || !proposal.matches("^(?!.*[?*\\\\/<>:\"|].*).+\\.(?!.*\\.+)\\S+$".toRegex()))
                    error = getString(R.string.invalid_name)
                inputLayout.error = error
                okButton.isEnabled = error == null
            }

            override fun afterTextChanged(e: Editable) {
            }
        })
        // OK button on keyboard
        inputField.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE && okButton.isEnabled) return@setOnEditorActionListener okButton.performClick()
            false
        }
    }

    /** Sets the list of all media of the tree complete of FileUri, to be used later by [updateFileOccurrences] */
    private fun setMediaList() {
        val mediaVisitor = MediaLeaders()
        Global.gc.accept(mediaVisitor)
        mediaVisitor.list.forEach { it.fileUri = FileUri(this, it.media) }
        mediaList = mediaVisitor.list
    }

    /**
     * Replaces the file link in all Media equal to [fileUri].
     * @param honorRelative Relative paths are kept
     * @return Array of objects modified on updating the file link
     */
    private fun updateFileOccurrences(newName: String, honorRelative: Boolean = true): Array<NoteContainer> {
        val finalPath = if (fileUri.relative && honorRelative) fileUri.media.file.replaceAfterLast('/', newName) else newName
        val filteredList = mediaList.filter { // Same fileUri path or same media link
            (fileUri.path != null && it.fileUri?.path == fileUri.path || it.media.file == fileUri.media.file) && it.media.file != finalPath
        }
        filteredList.forEach { it.media.file = finalPath }
        return filteredList.map { it.leader }.toTypedArray()
    }
}
