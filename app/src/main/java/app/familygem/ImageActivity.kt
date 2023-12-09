package app.familygem

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.familygem.constant.Extra
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder

class ImageActivity : AppCompatActivity() {
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.image_activity)
        // Fullscreen
        val layout = findViewById<CoordinatorLayout>(R.id.image_layout)
        WindowInsetsControllerCompat(window, layout).hide(WindowInsetsCompat.Type.statusBars())
        // Shows the file at maximum resolution
        val path = intent.getStringExtra(Extra.PATH)
        val glide = Glide.with(this)
        val builder: RequestBuilder<Drawable>
        val textView = findViewById<TextView>(R.id.image_info)
        if (path != null) {
            builder = glide.load(path)
            textView.text = path
        } else {
            val uriString = intent.getStringExtra(Extra.URI)
            val uri = Uri.parse(uriString)
            builder = glide.load(uri)
            textView.text = if (Global.settings.expert) Uri.decode(uriString) else uri.lastPathSegment
        }
        val imageView = findViewById<ImageView>(R.id.image_view)
        builder.into(imageView)
        imageView.setOnClickListener {
            textView.visibility = if (textView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }
}
