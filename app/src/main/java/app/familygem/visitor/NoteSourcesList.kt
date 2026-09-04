package app.familygem.visitor

import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer

/** Collects the list of all note-sources (SourceCitation without reference to a Source). */
class NoteSourcesList : TotalVisitor() {
    var list = mutableListOf<SourceCitation>()

    override fun visit(obj: ExtensionContainer, isLeader: Boolean): Boolean {
        if (obj is SourceCitationContainer) {
            list.addAll(obj.sourceCitations.filter { it.ref == null })
        }
        return true
    }
}
