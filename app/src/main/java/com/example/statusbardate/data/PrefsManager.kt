package com.example.statusbardate.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OverlayConfig(
    val isEnabled: Boolean = false,
    val formatOption: DateFormatOption = DateFormatOption.DD_MM,
    val offsetX: Int = 165,
    val offsetY: Int = 14,
    val textSizeSp: Float = 13f,
    val colorMode: TextColorMode = TextColorMode.AUTO
)

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("status_bar_date_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<OverlayConfig> = _config.asStateFlow()

    private fun loadConfig(): OverlayConfig {
        return OverlayConfig(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            formatOption = DateFormatOption.fromName(prefs.getString(KEY_FORMAT, null)),
            offsetX = prefs.getInt(KEY_OFFSET_X, 165),
            offsetY = prefs.getInt(KEY_OFFSET_Y, 14),
            textSizeSp = prefs.getFloat(KEY_TEXT_SIZE, 13f),
            colorMode = TextColorMode.fromName(prefs.getString(KEY_COLOR_MODE, null))
        )
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _config.update { it.copy(isEnabled = enabled) }
    }

    fun setFormat(option: DateFormatOption) {
        prefs.edit().putString(KEY_FORMAT, option.name).apply()
        _config.update { it.copy(formatOption = option) }
    }

    fun setOffset(x: Int, y: Int) {
        prefs.edit().putInt(KEY_OFFSET_X, x).putInt(KEY_OFFSET_Y, y).apply()
        _config.update { it.copy(offsetX = x, offsetY = y) }
    }

    fun setTextSize(sp: Float) {
        prefs.edit().putFloat(KEY_TEXT_SIZE, sp).apply()
        _config.update { it.copy(textSizeSp = sp) }
    }

    fun setColorMode(mode: TextColorMode) {
        prefs.edit().putString(KEY_COLOR_MODE, mode.name).apply()
        _config.update { it.copy(colorMode = mode) }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FORMAT = "format"
        private const val KEY_OFFSET_X = "offset_x"
        private const val KEY_OFFSET_Y = "offset_y"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_COLOR_MODE = "color_mode"

        @Volatile
        private var instance: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
