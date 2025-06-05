package app.familygem.visitor

import app.familygem.FileUri
import app.familygem.Global
import org.folg.gedcom.model.EventFact
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.Name
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.Visitor

/** Collects a list of Media. Can almost always replace [MediaContainerList]. */
class MediaList(private val gedcom: Gedcom?, private val request: Request = Request.ALL_MEDIA) : Visitor() {

    enum class Request {
        ALL_MEDIA, // Simple and shared media
        SIMPLE_MEDIA, // Only simple media (no Gedcom needed)
    }

    val list: MutableList<Media> = mutableListOf()

    private fun visitInternal(obj: MediaContainer): Boolean {
        when (request) {
            Request.ALL_MEDIA -> obj.getAllMedia(gedcom).forEach { // Shared and simple media of the object
                if (!list.contains(it)) list.add(it)
            }
            Request.SIMPLE_MEDIA -> list.addAll(obj.media) // Simple media only
        }
        return true
    }

    override fun visit(gedcom: Gedcom): Boolean {
        list.addAll(gedcom.media) // Finds all shared media of the tree
        return true
    }

    override fun visit(person: Person): Boolean {
        return visitInternal(person)
    }

    override fun visit(family: Family): Boolean {
        return visitInternal(family)
    }

    override fun visit(eventFact: EventFact): Boolean {
        return visitInternal(eventFact)
    }

    override fun visit(name: Name): Boolean {
        return visitInternal(name)
    }

    override fun visit(sourceCitation: SourceCitation): Boolean {
        return visitInternal(sourceCitation)
    }

    override fun visit(source: Source): Boolean {
        return visitInternal(source)
    }

    /** @return One random Media which should have preview (image, video or PDF). */
    fun getRandomPreviewMedia(): Media? {
        list.shuffle()
        val extensions = arrayOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "mp4", "mpg", "mov", "3gp", "webm", "mkv", "pdf"
        )
        return list.take(10).firstOrNull {
            val fileUri = FileUri(Global.context, it)
            fileUri.exists() && fileUri.extension in extensions // Local Media
                    || it.file != null && (it.file.startsWith("http://") || it.file.startsWith("https://")) // Media from the web
        }
    }
}
