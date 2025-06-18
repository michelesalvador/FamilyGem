package app.familygem.constant

import app.familygem.util.FileUtil

/** Possible file types resulting by [FileUtil.showImage]. */
enum class Type {
    /** Initial value. */
    NONE,

    /** Croppable image. */
    CROPPABLE,

    /** Not croppable video preview. */
    VIDEO,

    /** Not croppable PDF preview. */
    PDF,

    /** Generic file icon. */
    DOCUMENT,

    /** Image with preview from the web. */
    WEB_IMAGE,

    /** Anything else from the web. */
    WEB_ANYTHING,

    /** File failed loading. */
    PLACEHOLDER
}
