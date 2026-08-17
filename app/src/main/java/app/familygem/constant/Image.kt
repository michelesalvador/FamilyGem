package app.familygem.constant

import app.familygem.util.FileUtil

/** Some bitwise options to be passed to [FileUtil.showImage]. */
object Image {
    const val SOURCE = 0b1
    const val DARK = 0b10
    const val BLUR = 0b100
}
