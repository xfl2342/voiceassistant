package io.github.xfl2342.voiceassistant.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * 行程的急迫程度配色：越急越暖。
 *
 * 这里刻意不用主题色——主题色会跟随系统动态取色（Material You），
 * 那样「紧急」在别人的手机上可能变成绿色，颜色就不再是信息了。
 */
val UrgencyHigh = Color(0xFFE53935)
val UrgencyNormal = Color(0xFF1E88E5)
val UrgencyLow = Color(0xFF9E9E9E)
