package io.github.xfl2342.voiceassistant.data

/** 选择时间的方式。默认用滚轮，另外两种保留给不同习惯。 */
enum class TimeInputMode(val label: String, val description: String) {
    WHEEL("滚轮", "上下滑动选时间，像老式闹钟那样"),
    INPUT("直接输入", "两个数字框，直接敲出 23:45 这种时间"),
    DIAL("表盘", "点击钟面选时间"),
    ;

    companion object {
        val default: TimeInputMode = WHEEL

        fun fromName(name: String?): TimeInputMode =
            entries.firstOrNull { it.name == name } ?: default
    }
}
