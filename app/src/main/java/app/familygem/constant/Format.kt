package app.familygem.constant

/** All the formats a LocalDate can be displayed as. */
enum class Format(val pattern: String) {
    D_M_Y("d MMM yyy"), // 31 JAN 2000
    D_m_Y("d M yyy"), // 31 1 2000
    M_Y("MMM yyy"), // JAN 2000
    m_Y("M yyy"), // 1 2000
    D_M("d MMM"), // 31 JAN
    D("d"), // 31
    M("MMM"), // JAN
    Y("yyy"), // 2000
    OTHER("") // Empty or invalid date
}
