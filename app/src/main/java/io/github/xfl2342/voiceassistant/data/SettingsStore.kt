package io.github.xfl2342.voiceassistant.data

import android.content.Context
import java.time.DayOfWeek

/**
 * 本地设置读写。
 *
 * 目前只存 DeepSeek 相关配置，后续的提醒默认值、深色模式等也放这里。
 * 敏感的 API Key 会先加密再落盘。
 */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** DeepSeek API Key；未配置或解密失败时返回 null。 */
    var deepSeekApiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.let(CryptoBox::decrypt)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) {
                    remove(KEY_API_KEY)
                } else {
                    putString(KEY_API_KEY, CryptoBox.encrypt(value))
                }
            }.apply()
        }

    /** 是否已配置过 Key（不读出明文也能判断）。 */
    val hasApiKey: Boolean
        get() = prefs.contains(KEY_API_KEY)

    /** 使用的模型，默认 deepseek-chat。 */
    var deepSeekModel: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(KEY_MODEL, value).apply()
        }

    /** 选择时间的方式，默认滚轮。 */
    var timeInputMode: TimeInputMode
        get() = TimeInputMode.fromName(prefs.getString(KEY_TIME_INPUT, null))
        set(value) {
            prefs.edit().putString(KEY_TIME_INPUT, value.name).apply()
        }

    /** 每周从哪天开始，默认周一（国内习惯）。 */
    var weekStartDay: DayOfWeek
        get() = if (prefs.getString(KEY_WEEK_START, null) == DayOfWeek.SUNDAY.name) {
            DayOfWeek.SUNDAY
        } else {
            DayOfWeek.MONDAY
        }
        set(value) {
            prefs.edit().putString(KEY_WEEK_START, value.name).apply()
        }

    /** 深浅色模式。 */
    var themeMode: ThemeMode
        get() = ThemeMode.fromName(prefs.getString(KEY_THEME, null))
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_API_KEY = "deepseek_api_key"
        const val KEY_MODEL = "deepseek_model"
        const val KEY_TIME_INPUT = "time_input_mode"
        const val KEY_WEEK_START = "week_start_day"
        const val KEY_THEME = "theme_mode"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}
