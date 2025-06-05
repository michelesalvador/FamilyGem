package app.familygem.visitor

import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.NoteContainer

/**
 * Finds Media with their leader object.
 * Of a Gedcom finds all simple and shared Media (in case of shared media the leader will be the Media itself).
 * Of a MediaContainer finds all simple Media only.
 */
class MediaLeaders : TotalVisitor() {

    val list: MutableList<Pair<Media, NoteContainer>> = mutableListOf()
    private lateinit var leader: NoteContainer // The first object of the stack

    override fun visit(gedcom: Gedcom): Boolean {
        list.addAll(gedcom.media.map { Pair(it, it) }) // Collects shared media of the Gedcom
        return true
    }

    override fun visit(obj: ExtensionContainer, isLeader: Boolean): Boolean {
        if (isLeader && obj is NoteContainer) leader = obj
        if (obj is MediaContainer) {
            list.addAll(obj.media.map { Pair(it, leader) }) // Collects simple media of the object
        }
        return true
    }
}
