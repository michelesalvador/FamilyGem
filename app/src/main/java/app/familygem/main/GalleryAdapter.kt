package app.familygem.main

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.detail.MediaActivity
import app.familygem.util.FileUtil
import app.familygem.util.MediaUtil
import app.familygem.visitor.FindStack
import app.familygem.visitor.MediaLeaders.MediaWrapper
import org.folg.gedcom.model.Media
import java.util.Locale

/** Adapter for [GalleryFragment]. */
class GalleryAdapter(private val mediaList: List<MediaWrapper>) : RecyclerView.Adapter<GalleryAdapter.MediaViewHolder>(), Filterable {

    private val selectedMedia: MutableList<MediaWrapper> = mutableListOf() // Some media selected by the search feature

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.media_layout, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.setupMedia(position)
    }

    override fun getItemCount(): Int {
        return selectedMedia.size
    }

    inner class MediaViewHolder(var view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        lateinit var media: Media
        private val imageView: ImageView = view.findViewById(R.id.media_image)
        private val textView: TextView = view.findViewById(R.id.media_caption)
        private val numberView: TextView = view.findViewById(R.id.media_number)

        fun setupMedia(position: Int) {
            media = selectedMedia[position].media
            MediaUtil.furnishMedia(media, textView, numberView)
            view.setOnClickListener(this)
            view.setTag(R.id.tag_object, media)
            // Register context menu
            val activity = view.context as AppCompatActivity
            activity.supportFragmentManager.findFragmentById(R.id.main_fragment)!!.registerForContextMenu(view)
            if (selectedMedia[position].fileUri == null)
                selectedMedia[position].fileUri = FileUtil.showImage(media, imageView, 0, view.findViewById(R.id.media_progress), null)
            else FileUtil.showImage(media, imageView, 0, view.findViewById(R.id.media_progress), selectedMedia[position].fileUri)
        }

        override fun onClick(view: View) {
            val activity = view.context as MainActivity
            // Choosing a media record from GalleryFragment: returns the ID of the media record
            if (activity.intent.getBooleanExtra(Choice.MEDIA, false)) {
                val intent = Intent().putExtra(Extra.MEDIA_ID, media.id)
                activity.setResult(Activity.RESULT_OK, intent)
                activity.finish()
            } else { // Regular opening of MediaActivity
                val intent = Intent(view.context, MediaActivity::class.java)
                if (media.id != null) { // All the shared media
                    Memory.setLeader(media)
                } else { // Simple media from GalleryFragment
                    FindStack(Global.gc, media, true)
                    intent.putExtra(Extra.ALONE, true) // To make MediaActivity display the cabinet
                }
                view.context.startActivity(intent)
            }
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSequence: CharSequence): FilterResults {
                // Splits query by spaces and search all the words
                val query = charSequence.trim().toString().lowercase(Locale.getDefault()).split("\\s+".toRegex()).dropWhile { it.isEmpty() }
                selectedMedia.clear()
                if (query.isEmpty()) {
                    selectedMedia.addAll(mediaList)
                } else {
                    outer@ for (wrapper in mediaList) {
                        for (word in query) {
                            if (!wrapper.text.contains(word)) {
                                continue@outer
                            }
                        }
                        selectedMedia.add(wrapper)
                    }
                }
                val filterResults = FilterResults()
                filterResults.values = selectedMedia
                return filterResults
            }

            override fun publishResults(cs: CharSequence, fr: FilterResults) {
                notifyDataSetChanged()
            }
        }
    }
}
