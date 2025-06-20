package app.familygem.visitor

import app.familygem.FileUri
import app.familygem.main.GalleryFragment
import app.familygem.visitor.MediaLeaders.MediaWrapper
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.NoteContainer

/**
 * Finds all Media with their leader object (in case of shared media the leader will be the Media itself).
 * @param sharedMediaOnly Shared media only or both shared and simple media
 * @param findText The [MediaWrapper] collects also relevant text of the Media (to be searched by [GalleryFragment])
 */
class MediaLeaders(private val sharedMediaOnly: Boolean = false, private val findText: Boolean = false) : TotalVisitor() {

    val list: MutableList<MediaWrapper> = mutableListOf()
    private lateinit var leader: NoteContainer // The first object of the stack

    override fun visit(gedcom: Gedcom): Boolean {
        list.addAll(gedcom.media.map { MediaWrapper(it, it, findText) }) // Collects shared media of the Gedcom
        return true
    }

    override fun visit(obj: ExtensionContainer, isLeader: Boolean): Boolean {
        return if (sharedMediaOnly) false
        else {
            if (isLeader && obj is NoteContainer) leader = obj
            if (obj is MediaContainer) {
                list.addAll(obj.media.map { MediaWrapper(it, leader, findText) }) // Collects simple media of the object
            }
            true
        }
    }

    data class MediaWrapper(val media: Media, val leader: NoteContainer, private val findText: Boolean) {
        var fileUri: FileUri? = null
        lateinit var text: String // All relevant field values concatenated

        init {
            if (findText) {
                val fields: MutableList<String?> = mutableListOf(media.title, media.file, media.format)
                fields.addAll(media.notes.map { it.value })
                text = fields.filterNot { it == null }.joinToString(" ").lowercase()
            }
        }
    }
}
