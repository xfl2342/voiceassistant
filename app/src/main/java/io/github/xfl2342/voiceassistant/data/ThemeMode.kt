package io.github.xfl2342.voiceassistant.data

/** 深浅色模式。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    ;

    companion object {
        val default: ThemeMode = SYSTEM

        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: default
    }
}
