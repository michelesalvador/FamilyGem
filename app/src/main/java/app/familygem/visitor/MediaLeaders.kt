package app.familygem.visitor

import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.NoteContainer

/**
 * Finds all inline and shared media with their leader object.
 * In case of shared media, the leader is the Media itself.
 */
class MediaLeaders(val gedcom: Gedcom) : TotalVisitor() {

    val list: MutableList<Pair<Media, NoteContainer>> = mutableListOf()
    private lateinit var leader: NoteContainer // The first object of the stack

    override fun visit(gedcom: Gedcom): Boolean {
        list.addAll(gedcom.media.map { Pair(it, it) }) // Collects the shared media
        return true
    }

    override fun visit(obj: ExtensionContainer, isLeader: Boolean): Boolean {
        if (isLeader && obj is NoteContainer) leader = obj
        if (obj is MediaContainer) {
            list.addAll(obj.media.map { Pair(it, leader) })
        }
        return true
    }
}
