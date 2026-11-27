package com.example.statusbardate.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OverlayConfig(
    val isEnabled: Boolean = false,
    val formatOption: DateFormatOption = DateFormatOption.DD_MM,
    val fontStyle: FontStyleOption = FontStyleOption.SYSTEM_MEDIUM,
    val offsetX: Int = 165,
    val offsetY: Int = 14,
    val textSizeSp: Float = 13f,
    val colorMode: TextColorMode = TextColorMode.AUTO,
    val autoDetectLightApps: Boolean = true,
    val lightAppPackages: Set<String> = PrefsManager.DEFAULT_LIGHT_APPS
)

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("status_bar_date_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<OverlayConfig> = _config.asStateFlow()

    private fun loadConfig(): OverlayConfig {
        val savedPackages = prefs.getStringSet(KEY_LIGHT_APPS, null) ?: DEFAULT_LIGHT_APPS
        return OverlayConfig(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            formatOption = DateFormatOption.fromName(prefs.getString(KEY_FORMAT, null)),
            fontStyle = FontStyleOption.fromName(prefs.getString(KEY_FONT_STYLE, null)),
            offsetX = prefs.getInt(KEY_OFFSET_X, 165),
            offsetY = prefs.getInt(KEY_OFFSET_Y, 14),
            textSizeSp = prefs.getFloat(KEY_TEXT_SIZE, 13f),
            colorMode = TextColorMode.fromName(prefs.getString(KEY_COLOR_MODE, null)),
            autoDetectLightApps = prefs.getBoolean(KEY_AUTO_LIGHT_APPS, true),
            lightAppPackages = savedPackages.toSet()
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

    fun setFontStyle(style: FontStyleOption) {
        prefs.edit().putString(KEY_FONT_STYLE, style.name).apply()
        _config.update { it.copy(fontStyle = style) }
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

    fun setAutoDetectLightApps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LIGHT_APPS, enabled).apply()
        _config.update { it.copy(autoDetectLightApps = enabled) }
    }

    fun toggleLightAppPackage(packageName: String) {
        val current = _config.value.lightAppPackages.toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_LIGHT_APPS, current).apply()
        _config.update { it.copy(lightAppPackages = current) }
    }

    fun resetDefaultLightApps() {
        prefs.edit().putStringSet(KEY_LIGHT_APPS, DEFAULT_LIGHT_APPS).apply()
        _config.update { it.copy(lightAppPackages = DEFAULT_LIGHT_APPS) }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FORMAT = "format"
        private const val KEY_FONT_STYLE = "font_style"
        private const val KEY_OFFSET_X = "offset_x"
        private const val KEY_OFFSET_Y = "offset_y"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_COLOR_MODE = "color_mode"
        private const val KEY_AUTO_LIGHT_APPS = "auto_light_apps"
        private const val KEY_LIGHT_APPS = "light_app_packages"

        val DEFAULT_LIGHT_APPS: Set<String> = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.facebook.orca",
            "com.zing.zalo",
            "com.shopee.vn",
            "com.lazada.android",
            "vn.tiki.app.tikiandroid",
            "com.google.android.gm",
            "com.google.android.apps.docs",
            "com.google.android.apps.maps",
            "com.android.vending",
            "com.grabtaxi.passenger",
            "xyz.be.customer",
            "com.VCB",
            "com.mbmobile",
            "vn.com.techcombank.bb.app",
            "com.vnpay.bidv",
            "com.vietinbank.ipay",
            "com.mservice.momotransfer",
            "vn.com.vng.zalopay"
        )

        @Volatile
        private var instance: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
