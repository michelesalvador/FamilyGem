package app.familygem.constant

import app.familygem.util.FileUtil

/**
 * Possible image types resulting by [FileUtil.showImage].
 */
enum class Type {
    /** Initial value. */
    NONE,

    /** Croppable image. */
    CROPPABLE,

    /** Not croppable preview (video or PDF). */
    PREVIEW,

    /** Generic file icon. */
    DOCUMENT,

    /** Image failed loading. */
    PLACEHOLDER
}
