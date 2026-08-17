package app.familygem.visitor

import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitation

/**
 * Visitor somewhat complementary to [MediaReferences], having a double function:
 * - Edit the refs pointing to the shared Media
 * - Collect a list of containers that include the shared Media (if newId is null)
 */
class MediaContainers(gedcom: Gedcom, private val media: Media, private val newId: String? = null) : TotalVisitor() {
    @JvmField
    val containers = HashSet<MediaContainer>()

    init {
        gedcom.accept(this)
    }

    override fun visit(obj: ExtensionContainer, isLeader: Boolean): Boolean {
        if (obj is MediaContainer) {
            obj.mediaRefs.filter { it.ref == media.id }.forEach {
                if (newId != null) it.ref = newId
                containers.add(obj)
            }
        }
        return true
    }

    /** @return The first container found, preferred not Source nor SourceCitation */
    fun firstContainer(): MediaContainer? {
        return containers.firstOrNull { it !is Source && it !is SourceCitation } ?: containers.firstOrNull()
    }
}
