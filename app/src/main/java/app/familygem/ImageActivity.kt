package app.familygem

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.familygem.constant.Extra
import app.familygem.util.FileUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.signature.ObjectKey
import java.io.File

/** Activity to display and manage an image file. */
class ImageActivity : AppCompatActivity() {

    private var file: File? = null
    private var uri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.image_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Displays back arrow on toolbar
        displayImage()
    }

    /** Displays the image at maximum resolution. */
    private fun displayImage() {
        val path = intent.getStringExtra(Extra.PATH)
        val glide = Glide.with(this)
        val builder: RequestBuilder<Drawable>
        val textView = findViewById<TextView>(R.id.image_info)
        val pathOrUri: String
        if (path != null) {
            file = File(path)
            title = file?.name
            builder = glide.load(file)
            textView.text = path
            pathOrUri = path
        } else {
            val uriString = intent.getStringExtra(Extra.URI)
            uri = Uri.parse(uriString)
            title = FileUtil.extractFilename(this, uri!!, "")
            builder = glide.load(uri)
            textView.text = if (Global.settings.expert) Uri.decode(uriString) else uri?.lastPathSegment
            pathOrUri = uriString!!
        }
        // Clears Glide cache
        if (Global.croppedPaths.contains(pathOrUri)) {
            builder.signature(ObjectKey(Global.croppedPaths[pathOrUri]!!))
        }
        val imageView = findViewById<ImageView>(R.id.image_view)
        builder.into(imageView)
        imageView.setOnClickListener {
            textView.visibility = if (textView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    /** Redraws image after cropping. */
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(Extra.STRING)?.let { intent.putExtra(Extra.PATH, it) } // Updates image path in case it has changed
            displayImage()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 0, 0, R.string.crop)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0 -> FileUtil.cropImage(this, file, uri, cropLauncher)
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
        }
        return false
    }
}
