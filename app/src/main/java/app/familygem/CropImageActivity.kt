package app.familygem

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import app.familygem.databinding.CropImageActivityBinding
import app.familygem.util.toPxFloat
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Activity to crop image. */
class CropImageActivity : BaseActivity(), CropImageView.OnSetImageUriCompleteListener, CropImageView.OnCropImageCompleteListener {

    private lateinit var imageView: CropImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        CropImageActivityBinding.inflate(layoutInflater).run {
            setContentView(root)
            val imageUri = intent.data!!
            cropImageView.apply {
                setImageUriAsync(imageUri)
                setImageCropOptions(
                    CropImageOptions(
                        initialCropWindowPaddingRatio = .07F,
                        borderLineThickness = 1F.toPxFloat(),
                        borderCornerThickness = 4F.toPxFloat(),
                        borderCornerOffset = (-3F).toPxFloat()
                    )
                )
                customOutputUri = imageUri
                guidelines = CropImageView.Guidelines.OFF
                setOnCropImageCompleteListener(this@CropImageActivity)
            }
            imageView = cropImageView
        }
    }

    override fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?) {
        if (error != null) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
        if (result.isSuccessful) {
            setResult(RESULT_OK, Intent()) // For previous FileActivity
            Global.edited = true
            lifecycleScope.launch(Dispatchers.IO) {
                Glide.get(this@CropImageActivity).clearDiskCache()
                withContext(Dispatchers.Main) { onBackPressedDispatcher.onBackPressed() }
            }
        } else {
            Toast.makeText(this, result.error?.message, Toast.LENGTH_LONG).show()
        }
    }

    // Back arrow on actionbar
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.canhub.cropper.R.menu.crop_image_menu, menu)
        menu.findItem(com.canhub.cropper.R.id.crop_image_menu_crop).title = getString(R.string.done)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.canhub.cropper.R.id.ic_rotate_right_24 -> {
                imageView.rotateImage(90)
                true
            }
            com.canhub.cropper.R.id.ic_flip_24_horizontally -> {
                imageView.flipImageHorizontally()
                true
            }
            com.canhub.cropper.R.id.ic_flip_24_vertically -> {
                imageView.flipImageVertically()
                true
            }
            com.canhub.cropper.R.id.crop_image_menu_crop -> {
                imageView.croppedImageAsync()
                true
            }
            else -> false
        }
    }
}
