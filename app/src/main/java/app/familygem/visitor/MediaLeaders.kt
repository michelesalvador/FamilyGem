package app.familygem.visitor

import app.familygem.FileUri
import app.familygem.constant.Image
import app.familygem.main.GalleryFragment
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitation

/**
 * Finds all Media with their leader object (in case of shared media the leader will be the Media itself).
 * Finds also the MediaContainer of each Media (in case of shared media the first one).
 * @param sharedMediaOnly Shared media only or both shared and simple media
 * @param findText The [MediaWrapper] collects also relevant text of the Media (to be searched by [GalleryFragment])
 */
class MediaLeaders(private val sharedMediaOnly: Boolean = false, private val findText: Boolean = false) : TotalVisitor() {

    val list: MutableList<MediaWrapper> = mutableListOf()
    private lateinit var leader: NoteContainer // The first object of the stack

    // Collects shared media of the GEDCOM
    override fun visit(gedcom: Gedcom): Boolean {
        list.addAll(gedcom.media.map { media ->
            // TODO: It works but is probably inefficient to create a visitor for each shared media since we are already in a visitor
            val container = MediaContainers(gedcom, media).firstContainer()
            MediaWrapper(media, media, container, findText)
        })
        return true
    }

    // Collects simple media of the object
    override fun visit(obj: ExtensionContainer, isLeader: Boolean): Boolean {
        return if (sharedMediaOnly) false
        else {
            if (isLeader && obj is NoteContainer) leader = obj
            if (obj is MediaContainer) {
                list.addAll(obj.media.map { MediaWrapper(it, leader, obj, findText) })
            }
            true
        }
    }

    data class MediaWrapper(val media: Media, val leader: NoteContainer, val container: MediaContainer?, private val findText: Boolean) {
        var fileUri: FileUri? = null
        lateinit var text: String // All relevant field values concatenated

        fun getOptions(): Int {
            return if (container is Source || container is SourceCitation) Image.SOURCE else 0
        }

        init {
            if (findText) {
                val fields: MutableList<String?> = mutableListOf(media.title, media.file, media.format)
                fields.addAll(media.notes.map { it.value })
                text = fields.filterNot { it == null }.joinToString(" ").lowercase()
            }
        }
    }
}
