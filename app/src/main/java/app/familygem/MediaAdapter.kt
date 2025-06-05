package app.familygem

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.detail.MediaActivity
import app.familygem.main.MainActivity
import app.familygem.profile.ProfileActivity
import app.familygem.util.FileUtil
import app.familygem.util.MediaUtil
import app.familygem.visitor.FindStack
import app.familygem.visitor.MediaContainerList.MediaWrapper
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Person

/** Adapter for a media gallery made with RecyclerView. */
class MediaAdapter(private val mediaList: List<MediaWrapper>, private val detail: Boolean) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.media_layout, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.setupMedia(position)
    }

    override fun getItemCount(): Int {
        return mediaList.size
    }

    inner class MediaViewHolder(var view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        lateinit var media: Media
        lateinit var container: Any
        private val imageView: ImageView = view.findViewById(R.id.media_image)
        private val textView: TextView = view.findViewById(R.id.media_caption)
        private val numberView: TextView = view.findViewById(R.id.media_number)

        fun setupMedia(position: Int) {
            media = mediaList[position].media
            container = mediaList[position].container
            if (detail) {
                MediaUtil.furnishMedia(media, textView, numberView)
                view.setOnClickListener(this)
                view.setTag(R.id.tag_object, media)
                view.setTag(R.id.tag_container, container)
                // Register context menu
                when (val activity = view.context as AppCompatActivity) {
                    is ProfileActivity -> activity.getPageFragment(0).registerForContextMenu(view) // profile.MediaFragment
                    is MainActivity -> // main.MediaFragment
                        activity.getSupportFragmentManager().findFragmentById(R.id.main_fragment)!!.registerForContextMenu(view)
                    else -> activity.registerForContextMenu(view) // DetailActivity
                }
            } else {
                val params = RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, U.dpToPx(110F))
                val margin = U.dpToPx(5F)
                params.setMargins(margin, margin, margin, margin)
                view.layoutParams = params
                textView.visibility = View.GONE
                numberView.visibility = View.GONE
            }
            if (mediaList[position].fileUri == null)
                mediaList[position].fileUri = FileUtil.showImage(media, imageView, 0, view.findViewById(R.id.media_progress), null)
            else FileUtil.showImage(media, imageView, 0, view.findViewById(R.id.media_progress), mediaList[position].fileUri)
        }

        override fun onClick(view: View) {
            val activity = view.context as AppCompatActivity
            // Choosing a media record from main.MediaFragment: returns the ID of the media record
            if (activity.intent.getBooleanExtra(Choice.MEDIA, false)) {
                val intent = Intent().putExtra(Extra.MEDIA_ID, media.id)
                activity.setResult(Activity.RESULT_OK, intent)
                activity.finish()
            } else { // Regular opening of MediaActivity
                val intent = Intent(view.context, MediaActivity::class.java)
                if (media.id != null) { // All the media records
                    Memory.setLeader(media)
                } // First-level media in profile.MediaFragment, or simple media in DetailActivity
                else if ((activity is ProfileActivity && container is Person) || activity is DetailActivity) {
                    Memory.add(media)
                } else { // Simple media from main.MediaFragment, or sub-level media from profile.MediaFragment
                    FindStack(Global.gc, media, true)
                    if (activity is MainActivity)  // In main.MediaFragment only
                        intent.putExtra(Extra.ALONE, true) // To make MediaActivity display the cabinet
                }
                view.context.startActivity(intent)
            }
        }
    }

    /** RecyclerView to make media icons insensitive to clicks.
     * TODO: however prevents scrolling in Detail
     */
    class UnclickableRecyclerView(context: Context) : RecyclerView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            return false // The grid does not intercept the click
        }
    }
}
