package com.example.statusbardate.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OverlayConfig(
    val isEnabled: Boolean = false,
    val formatOption: DateFormatOption = DateFormatOption.DD_MM,
    val fontStyle: FontStyleOption = FontStyleOption.OPPO_SANS,
    val fontWeight: Int = 550,
    val selectedOemFontId: String? = null,
    val customFontPath: String? = null,
    val autoMatchClock: Boolean = true,
    val verticalNudgePx: Int = 0,
    val offsetX: Int = PrefsManager.DEFAULT_OFFSET_X,
    val offsetY: Int = PrefsManager.DEFAULT_OFFSET_Y,
    val textSizeSp: Float = PrefsManager.DEFAULT_TEXT_SIZE_SP,
    val colorMode: TextColorMode = TextColorMode.AUTO,
    val autoDetectLightApps: Boolean = true,
    val lightAppPackages: Set<String> = PrefsManager.DEFAULT_LIGHT_APPS,
    val autoDodgeNotifications: Boolean = true,
    val iconStepPx: Int = PrefsManager.DEFAULT_ICON_STEP_PX,
    val maxDodgeIcons: Int = 4
)

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("status_bar_date_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<OverlayConfig> = _config.asStateFlow()

    private fun loadConfig(): OverlayConfig {
        val savedPackages = prefs.getStringSet(KEY_LIGHT_APPS, null) ?: DEFAULT_LIGHT_APPS

        var loadedOffsetX = prefs.getInt(KEY_OFFSET_X, DEFAULT_OFFSET_X)
        if (loadedOffsetX == 165) {
            loadedOffsetX = DEFAULT_OFFSET_X
            prefs.edit().putInt(KEY_OFFSET_X, loadedOffsetX).apply()
        }

        var loadedStepPx = prefs.getInt(KEY_ICON_STEP_PX, DEFAULT_ICON_STEP_PX)
        if (loadedStepPx == 56) {
            loadedStepPx = DEFAULT_ICON_STEP_PX
            prefs.edit().putInt(KEY_ICON_STEP_PX, loadedStepPx).apply()
        }

        return OverlayConfig(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            formatOption = DateFormatOption.fromName(prefs.getString(KEY_FORMAT, null)),
            fontStyle = FontStyleOption.fromName(prefs.getString(KEY_FONT_STYLE, null)),
            fontWeight = prefs.getInt(KEY_FONT_WEIGHT, 550),
            selectedOemFontId = prefs.getString(KEY_OEM_FONT_ID, null),
            customFontPath = prefs.getString(KEY_CUSTOM_FONT_PATH, null),
            autoMatchClock = prefs.getBoolean(KEY_AUTO_MATCH_CLOCK, true),
            verticalNudgePx = prefs.getInt(KEY_VERTICAL_NUDGE_PX, 0),
            offsetX = loadedOffsetX,
            offsetY = prefs.getInt(KEY_OFFSET_Y, DEFAULT_OFFSET_Y),
            textSizeSp = prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE_SP),
            colorMode = TextColorMode.fromName(prefs.getString(KEY_COLOR_MODE, null)),
            autoDetectLightApps = prefs.getBoolean(KEY_AUTO_LIGHT_APPS, true),
            lightAppPackages = savedPackages.toSet(),
            autoDodgeNotifications = prefs.getBoolean(KEY_AUTO_DODGE_NOTIF, true),
            iconStepPx = loadedStepPx,
            maxDodgeIcons = prefs.getInt(KEY_MAX_DODGE_ICONS, 4)
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

    fun setFontWeight(weight: Int) {
        val clamped = weight.coerceIn(300, 700)
        prefs.edit().putInt(KEY_FONT_WEIGHT, clamped).apply()
        _config.update { it.copy(fontWeight = clamped) }
    }

    fun setSelectedOemFontId(id: String?) {
        prefs.edit().putString(KEY_OEM_FONT_ID, id).apply()
        _config.update { it.copy(selectedOemFontId = id, fontStyle = FontStyleOption.OPPO_SANS) }
    }

    fun setCustomFontPath(path: String?) {
        prefs.edit().putString(KEY_CUSTOM_FONT_PATH, path).apply()
        _config.update { it.copy(customFontPath = path, fontStyle = FontStyleOption.CUSTOM_FILE) }
    }

    fun setAutoMatchClock(autoMatch: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_MATCH_CLOCK, autoMatch).apply()
        _config.update { it.copy(autoMatchClock = autoMatch) }
    }

    fun setVerticalNudgePx(nudgePx: Int) {
        val clamped = nudgePx.coerceIn(-20, 20)
        prefs.edit().putInt(KEY_VERTICAL_NUDGE_PX, clamped).apply()
        _config.update { it.copy(verticalNudgePx = clamped) }
    }

    fun setOffset(x: Int, y: Int) {
        prefs.edit().putInt(KEY_OFFSET_X, x).putInt(KEY_OFFSET_Y, y).apply()
        _config.update { it.copy(offsetX = x, offsetY = y) }
    }

    fun setOffsetX(x: Int) {
        prefs.edit().putInt(KEY_OFFSET_X, x).apply()
        _config.update { it.copy(offsetX = x) }
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

    fun setAutoDodgeNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DODGE_NOTIF, enabled).apply()
        _config.update { it.copy(autoDodgeNotifications = enabled) }
    }

    fun setIconStepPx(stepPx: Int) {
        prefs.edit().putInt(KEY_ICON_STEP_PX, stepPx).apply()
        _config.update { it.copy(iconStepPx = stepPx) }
    }

    fun setMaxDodgeIcons(maxIcons: Int) {
        prefs.edit().putInt(KEY_MAX_DODGE_ICONS, maxIcons).apply()
        _config.update { it.copy(maxDodgeIcons = maxIcons) }
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
        const val DEFAULT_OFFSET_X = 186
        const val DEFAULT_OFFSET_Y = 14
        const val DEFAULT_TEXT_SIZE_SP = 14f
        const val DEFAULT_ICON_STEP_PX = 68

        private const val KEY_ENABLED = "enabled"
        private const val KEY_FORMAT = "format"
        private const val KEY_FONT_STYLE = "font_style"
        private const val KEY_FONT_WEIGHT = "font_weight"
        private const val KEY_OEM_FONT_ID = "oem_font_id"
        private const val KEY_CUSTOM_FONT_PATH = "custom_font_path"
        private const val KEY_AUTO_MATCH_CLOCK = "auto_match_clock"
        private const val KEY_VERTICAL_NUDGE_PX = "vertical_nudge_px"
        private const val KEY_OFFSET_X = "offset_x"
        private const val KEY_OFFSET_Y = "offset_y"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_COLOR_MODE = "color_mode"
        private const val KEY_AUTO_LIGHT_APPS = "auto_light_apps"
        private const val KEY_LIGHT_APPS = "light_app_packages"
        private const val KEY_AUTO_DODGE_NOTIF = "auto_dodge_notif"
        private const val KEY_ICON_STEP_PX = "icon_step_px"
        private const val KEY_MAX_DODGE_ICONS = "max_dodge_icons"

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
