package app.familygem.util

import android.view.View
import android.widget.TextView
import app.familygem.Global
import app.familygem.Memory
import app.familygem.U
import app.familygem.visitor.FindStack
import app.familygem.visitor.MediaContainers
import app.familygem.visitor.MediaList
import app.familygem.visitor.MediaReferences
import com.google.gson.Gson
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.MediaRef

/** Removes from a container all references to the shared media with the given ID. */
fun MediaContainer.unlinkMedia(mediaId: String) {
    with(mediaRefs.iterator()) {
        forEach { if (it.ref == mediaId) remove() }
    }
    if (mediaRefs.isEmpty()) mediaRefs = null
}

object MediaUtil {

    /** Creates a new shared media, optionally inside a container record. */
    fun newSharedMedia(container: MediaContainer? = null): Media {
        val media = Media()
        media.id = U.newID(Global.gc, Media::class.java)
        media.fileTag = "FILE" // Necessary to then export the GEDCOM
        Global.gc.addMedia(media)
        if (container != null) {
            val mediaRef = MediaRef()
            mediaRef.ref = media.id
            container.addMediaRef(mediaRef)
        }
        return media
    }

    /**
     * Converts the given shared media to simple media in all its containers.
     * @return An array of the modified leader objects
     */
    fun makeSimpleMedia(media: Media): Array<Any> {
        val mediaContainers = MediaContainers(Global.gc, media, null)
        val mediaReferences = MediaReferences(Global.gc, media, false)
        val mediaId = media.id // We are going to null it
        Global.gc.media.remove(media)
        if (Global.gc.media.isEmpty()) Global.gc.media = null
        mediaContainers.containers.forEach { container ->
            container.unlinkMedia(mediaId)
            val mediaCopy = if (media.id != null) { // First we reuse original media
                media.apply {
                    id = null
                    change = null
                }
            } else { // Then media must be cloned
                val gson = Gson()
                gson.fromJson(gson.toJson(media), Media::class.java)
            }
            container.addMedia(mediaCopy)
        }
        Global.gc.createIndexes()
        return mediaReferences.leaders.toTypedArray()
    }

    /**
     * Converts the given simple media and all identical media to a shared media.
     * @return An array of modified leader objects plus the media itself
     */
    fun makeSharedMedia(media: Media): Array<Any> {
        val mediaList = MediaList(null, MediaList.Request.SIMPLE_MEDIA)
        Global.gc.accept(mediaList)
        val newId = U.newID(Global.gc, Media::class.java)
        val leaders = mutableSetOf<Any>()
        mediaList.list.forEach {
            // The media itself and all the very similar media
            if (areIdenticalMedia(it, media)) {
                val stack = FindStack(Global.gc, it, false)
                (stack.containerObject as MediaContainer).apply {
                    this.media.remove(it)
                    if (this.media.isEmpty()) this.media = null
                    val mediaRef = MediaRef()
                    mediaRef.ref = newId
                    addMediaRef(mediaRef)
                    leaders.add(stack.leaderObject)
                }
            }
        }
        media.id = newId
        Global.gc.addMedia(media)
        leaders.add(media)
        return leaders.toTypedArray()
    }

    /** Two simple media have the same content. */
    private fun areIdenticalMedia(media1: Media, media: Media): Boolean {
        media1.run {
            return file == media.file && title == media.title && format == media.format
                    && notes.map { it.value } == media.notes.map { it.value }
        }
    }

    /**
     * Deletes a shared or simple media and removes the references in container records.
     * @return An array with the modified leader objects
     */
    fun deleteMedia(media: Media): Array<Any> {
        Memory.setInstanceAndAllSubsequentToNull(media)
        return if (media.id != null) { // Shared Media
            Global.gc.media.remove(media)
            val references = MediaReferences(Global.gc, media, true) // Delete references in all containers
            references.leaders.toTypedArray()
        } else { // Simple Media
            val stack = FindStack(Global.gc, media, false) // The stack of the media provides container and leader object
            val container = stack.containerObject as MediaContainer
            container.media.remove(media)
            if (container.media.isEmpty()) container.media = null
            arrayOf(stack.leaderObject)
        }
    }

    /** Fills in two text views of a media. */
    fun furnishMedia(media: Media, textView: TextView, numberView: TextView) {
        // File title and name
        var label = media.title ?: ""
        if (Global.settings.expert && media.file != null) {
            var file = media.file
            file = file.replace('\\', '/')
            if (file.lastIndexOf('/') > -1) {
                if (file.length > 1 && file.endsWith("/")) // Removes last slash
                    file = file.substring(0, file.length - 1)
                file = file.substring(file.lastIndexOf('/') + 1)
            }
            label += "\n$file"
        }
        textView.apply {
            if (label.isEmpty()) visibility = View.GONE
            else {
                text = label.trim('\n', ' ')
                visibility = View.VISIBLE
            }
        }
        // Usage number
        if (media.id != null) {
            val mediaReferences = MediaReferences(Global.gc, media, false)
            numberView.text = mediaReferences.num.toString()
            numberView.visibility = View.VISIBLE
        } else numberView.visibility = View.GONE
    }
}
