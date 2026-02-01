package app.familygem.constant

import app.familygem.util.FileUtil

/** Some file types that can be exported, with their MIME type. */
enum class FileType(val mimeType: String) {
    PNG("image/png"),
    PDF("application/pdf"),
    ZIP_BACKUP(FileUtil.zipMimeTypes[0]),
    GEDCOM(FileUtil.gedcomMimeTypes[0]),
    ZIPPED_GEDCOM(FileUtil.zipMimeTypes[0])
}
