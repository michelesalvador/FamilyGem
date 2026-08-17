package app.familygem.constant

/** Level of detail to display sources and notes. */
enum class Level {
    DETAILED, MEDIUM, SMALL;

    fun lower(): Level {
        return when (this) {
            DETAILED -> MEDIUM
            MEDIUM -> SMALL
            SMALL -> SMALL
        }
    }
}
