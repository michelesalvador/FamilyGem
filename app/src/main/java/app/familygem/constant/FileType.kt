package app.familygem.constant

import app.familygem.util.FileUtil

/** Some file types that can be exported, with their MIME type. */
enum class FileType(val mimeType: String) {
    PNG("image/png"),
    PDF("application/pdf"),
    ZIP_BACKUP("application/zip"),
    GEDCOM(FileUtil.gedcomMimeTypes[0]),
    ZIPPED_GEDCOM("application/zip")
}
