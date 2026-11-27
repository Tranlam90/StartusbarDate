package com.example.statusbardate.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Xml
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class DateFormatOption(val pattern: String, val label: String) {
    DD_MM("dd/MM", "dd/MM (27/11)"),
    DD_MM_DASH("dd-MM", "dd-MM (27-11)"),
    DD_MM_YYYY("dd/MM/yyyy", "dd/MM/yyyy (27/11/2026)");

    fun format(date: LocalDate = LocalDate.now()): String {
        return date.format(DateTimeFormatter.ofPattern(pattern))
    }

    companion object {
        fun fromName(name: String?): DateFormatOption {
            return entries.find { it.name == name } ?: DD_MM
        }
    }
}

enum class TextColorMode(val label: String) {
    AUTO("Tự động (Hệ thống + App)"),
    WHITE("Luôn chữ trắng"),
    BLACK("Luôn chữ đen");

    companion object {
        fun fromName(name: String?): TextColorMode {
            return entries.find { it.name == name } ?: AUTO
        }
    }
}

object OemFontResolver {
    data class ResolvedFont(
        val id: String,
        val typeface: Typeface,
        val sourceName: String,
        val priority: Int
    )

    private val cachedCandidates: List<ResolvedFont> by lazy {
        discoverSystemFonts()
    }

    private fun discoverSystemFonts(): List<ResolvedFont> {
        val candidatePaths = LinkedHashSet<String>()

        // 1. Font tùy biến nếu người dùng đổi font trong OPPO Theme Store
        val themePaths = listOf(
            "/data/system/theme/fonts/Roboto-Medium.ttf",
            "/data/system/theme/fonts/Roboto-Regular.ttf",
            "/data/format_unclear/theme/fonts/Roboto-Regular.ttf"
        )
        candidatePaths.addAll(themePaths)

        // 2. Đọc trực tiếp file cấu hình XML font hệ thống (không bị SELinux chặn listFiles)
        val xmlConfigs = listOf(
            "/system_ext/etc/fonts_base.xml",
            "/product/etc/fonts_customization.xml",
            "/system/etc/fonts.xml",
            "/system/etc/fonts_fallback.xml",
            "/odm/etc/fonts_customization.xml"
        )
        val fontDirs = listOf(
            "/system/fonts/",
            "/system_ext/fonts/",
            "/product/fonts/",
            "/odm/fonts/"
        )

        for (xmlPath in xmlConfigs) {
            val fileNames = parseFontFilenamesFromXml(xmlPath)
            for (fn in fileNames) {
                if (fn.startsWith("/")) {
                    candidatePaths.add(fn)
                } else {
                    for (dir in fontDirs) {
                        candidatePaths.add(dir + fn)
                    }
                }
            }
        }

        // 3. Danh sách đường dẫn trực tiếp các file font OPPO / ColorOS / OplusSans / SysFont
        val explicitNames = listOf(
            "OplusSans4.0_Medium.ttf",
            "OplusSans4.0_Regular.ttf",
            "OplusSans4.0.ttf",
            "OplusSans3.0_Medium.ttf",
            "OplusSans3.0_Regular.ttf",
            "OplusSans3.0.ttf",
            "OplusSans-Medium.ttf",
            "OplusSans-Regular.ttf",
            "OPPOSans4.0.ttf",
            "OPPOSans3.0.ttf",
            "OPPOSans-Medium.ttf",
            "OPPOSans-Regular.ttf",
            "OPPOSans-VF.ttf",
            "SysFont-Static-Medium.ttf",
            "SysFont-Static-Regular.ttf",
            "SysFont-Medium.ttf",
            "SysFont-Regular.ttf",
            "SysFont-VF.ttf",
            "OnePlusSans-Medium.ttf",
            "OnePlusSans-Regular.ttf"
        )
        for (dir in fontDirs) {
            for (name in explicitNames) {
                candidatePaths.add(dir + name)
            }
        }

        val excludedTokens = listOf(
            "italic", "condensed", "mono", "serif", "emoji",
            "arabic", "hebrew", "thai", "myanmar", "khmer", "lao",
            "devanagari", "bengali", "tamil", "telugu", "kannada",
            "malayalam", "gujarati", "gurmukhi", "oriya", "sinhala",
            "cjk", "sc", "tc", "jp", "kr", "hk", "hans", "hant"
        )

        val results = mutableListOf<ResolvedFont>()
        val seenFileNames = HashSet<String>()

        for (path in candidatePaths) {
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) continue

            val name = file.name
            val lower = name.lowercase()
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue
            if (excludedTokens.any { lower.contains(it) }) continue

            val isOem = lower.contains("oplus") ||
                lower.contains("oppo") ||
                lower.contains("sysfont") ||
                lower.contains("oneplus") ||
                path.contains("/theme/fonts/")

            if (!isOem) continue
            if (!seenFileNames.add(name)) continue

            val tf = runCatching { Typeface.createFromFile(file) }.getOrNull() ?: continue
            val priority = when {
                path.contains("/theme/fonts/") -> 0
                lower.contains("medium") -> 1
                lower.contains("vf") || lower.contains("4.0") || lower.contains("3.0") -> 2
                lower.contains("regular") -> 3
                else -> 4
            }
            results.add(
                ResolvedFont(
                    id = path,
                    typeface = tf,
                    sourceName = name,
                    priority = priority
                )
            )
        }

        // Thử thêm tên family nội bộ của ColorOS nếu chưa thấy file trực tiếp
        val oemFamilies = listOf("oplus-sans", "oppo-sans", "sys-sans-en", "oneplus-sans")
        for (fam in oemFamilies) {
            val tf = runCatching { Typeface.create(fam, Typeface.NORMAL) }.getOrNull()
            if (tf != null && tf != Typeface.DEFAULT && tf != Typeface.SANS_SERIF) {
                results.add(
                    ResolvedFont(
                        id = "family:$fam",
                        typeface = tf,
                        sourceName = fam,
                        priority = 5
                    )
                )
            }
        }

        if (results.isEmpty()) {
            results.add(
                ResolvedFont(
                    id = "sans-serif-medium",
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL),
                    sourceName = "sans-serif-medium",
                    priority = 10
                )
            )
        }

        return results.sortedWith(compareBy<ResolvedFont> { it.priority }.thenBy { it.sourceName })
    }

    private fun parseFontFilenamesFromXml(xmlPath: String): List<String> {
        val file = File(xmlPath)
        if (!file.exists() || !file.canRead()) return emptyList()
        val names = mutableListOf<String>()
        runCatching {
            file.inputStream().use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")
                var eventType = parser.eventType
                var insideFontTag = false
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "font") {
                                insideFontTag = true
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (insideFontTag) {
                                val text = parser.text?.trim().orEmpty()
                                if (text.endsWith(".ttf", ignoreCase = true) ||
                                    text.endsWith(".otf", ignoreCase = true)
                                ) {
                                    names.add(text)
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "font") {
                                insideFontTag = false
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        }
        return names
    }

    fun getAllCandidates(): List<ResolvedFont> = cachedCandidates

    fun resolveById(selectedId: String?): ResolvedFont {
        if (!selectedId.isNullOrBlank()) {
            cachedCandidates.find { it.id == selectedId }?.let { return it }
        }
        return cachedCandidates.first()
    }

    fun loadCustomFontFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            val outFile = File(context.filesDir, "custom_status_bar_font.ttf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            val tf = Typeface.createFromFile(outFile) ?: return null
            if (tf == Typeface.DEFAULT) return null
            outFile.absolutePath
        }.getOrNull()
    }

    fun loadCustomFontFile(path: String?): Typeface? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }
}

enum class FontStyleOption(val label: String) {
    OPPO_SANS("OPPO / ColorOS (Khớp giờ)"),
    CUSTOM_FILE("File font (.ttf) tự chọn"),
    SYSTEM_MEDIUM("Chuẩn Android (Medium)"),
    SYSTEM_REGULAR("Hệ thống (Thường)"),
    SYSTEM_SEMIBOLD("Hệ thống (Đậm)"),
    SANS_LIGHT("Nét mảnh (Light)"),
    SANS_CONDENSED("Chữ gọn (Condensed)"),
    MONOSPACE("Số đều (Monospace)");

    fun toAndroidTypeface(
        weight: Int = 550,
        selectedOemFontId: String? = null,
        customFontPath: String? = null
    ): Typeface {
        val clampedWeight = weight.coerceIn(300, 700)
        return when (this) {
            OPPO_SANS -> {
                val base = OemFontResolver.resolveById(selectedOemFontId).typeface
                Typeface.create(base, clampedWeight, false)
            }
            CUSTOM_FILE -> {
                val custom = OemFontResolver.loadCustomFontFile(customFontPath)
                if (custom != null) {
                    Typeface.create(custom, clampedWeight, false)
                } else {
                    val base = OemFontResolver.resolveById(selectedOemFontId).typeface
                    Typeface.create(base, clampedWeight, false)
                }
            }
            SYSTEM_MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            SYSTEM_REGULAR -> Typeface.create("sans-serif", Typeface.NORMAL)
            SYSTEM_SEMIBOLD -> Typeface.create("sans-serif-medium", Typeface.BOLD)
            SANS_LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            SANS_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            MONOSPACE -> Typeface.MONOSPACE
        }
    }

    fun toComposeFontFamily(
        selectedOemFontId: String? = null,
        customFontPath: String? = null
    ): FontFamily {
        return when (this) {
            MONOSPACE -> FontFamily.Monospace
            OPPO_SANS, CUSTOM_FILE -> {
                val tf = toAndroidTypeface(550, selectedOemFontId, customFontPath)
                FontFamily(tf)
            }
            else -> FontFamily.Default
        }
    }

    fun toComposeFontWeight(weight: Int = 550): FontWeight {
        return when (this) {
            OPPO_SANS, CUSTOM_FILE -> FontWeight(weight.coerceIn(300, 700))
            SYSTEM_REGULAR, SANS_CONDENSED -> FontWeight.Normal
            SYSTEM_MEDIUM -> FontWeight.Medium
            SYSTEM_SEMIBOLD -> FontWeight.SemiBold
            SANS_LIGHT -> FontWeight.Light
            MONOSPACE -> FontWeight.Normal
        }
    }

    companion object {
        fun fromName(name: String?): FontStyleOption {
            return entries.find { it.name == name } ?: OPPO_SANS
        }
    }
}
