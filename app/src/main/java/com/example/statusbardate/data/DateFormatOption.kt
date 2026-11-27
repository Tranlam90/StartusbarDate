package com.example.statusbardate.data

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
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
        val typeface: Typeface,
        val sourceName: String
    )

    private val cachedResolved: ResolvedFont by lazy {
        val fontDirs = listOf("/system/fonts", "/system_ext/fonts", "/product/fonts")
        val priorityTokens = listOf("oplussans", "opposans", "sysfont", "oneplussans")
        val excludedTokens = listOf("italic", "condensed", "emoji", "arabic", "cjk", "sc", "tc", "jp", "kr")

        val foundFiles = mutableListOf<File>()
        for (dirPath in fontDirs) {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                dir.listFiles()?.forEach { file ->
                    val lower = file.name.lowercase()
                    if ((lower.endsWith(".ttf") || lower.endsWith(".otf")) &&
                        priorityTokens.any { lower.contains(it) } &&
                        excludedTokens.none { lower.contains(it) }
                    ) {
                        foundFiles.add(file)
                    }
                }
            }
        }

        // Ưu tiên bản Medium hoặc Variable Font (VF / 3.0 / 4.0)
        val sortedFiles = foundFiles.sortedBy { file ->
            val lower = file.name.lowercase()
            when {
                lower.contains("medium") -> 0
                lower.contains("vf") || lower.contains("4.0") || lower.contains("3.0") -> 1
                lower.contains("regular") -> 2
                else -> 3
            }
        }

        for (file in sortedFiles) {
            if (file.canRead()) {
                val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
                if (tf != null) {
                    return@lazy ResolvedFont(tf, file.name)
                }
            }
        }

        val familyNames = listOf(
            "oplus-sans",
            "oppo-sans",
            "sys-sans-en",
            "sans-serif-medium"
        )
        for (family in familyNames) {
            val tf = runCatching { Typeface.create(family, Typeface.NORMAL) }.getOrNull()
            if (tf != null && tf != Typeface.DEFAULT) {
                return@lazy ResolvedFont(tf, family)
            }
        }

        ResolvedFont(Typeface.create("sans-serif-medium", Typeface.NORMAL), "sans-serif-medium")
    }

    fun resolveOppoOrSystem(): ResolvedFont = cachedResolved
}

enum class FontStyleOption(val label: String) {
    OPPO_SANS("OPPO / ColorOS (Khớp giờ)"),
    SYSTEM_MEDIUM("Chuẩn thanh trạng thái (Medium)"),
    SYSTEM_REGULAR("Hệ thống (Thường)"),
    SYSTEM_SEMIBOLD("Hệ thống (Đậm)"),
    SANS_LIGHT("Nét mảnh (Light)"),
    SANS_CONDENSED("Chữ gọn (Condensed)"),
    MONOSPACE("Số đều (Monospace)");

    fun toAndroidTypeface(weight: Int = 550): Typeface {
        val clampedWeight = weight.coerceIn(300, 700)
        return when (this) {
            OPPO_SANS -> {
                val base = OemFontResolver.resolveOppoOrSystem().typeface
                Typeface.create(base, clampedWeight, false)
            }
            SYSTEM_MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            SYSTEM_REGULAR -> Typeface.create("sans-serif", Typeface.NORMAL)
            SYSTEM_SEMIBOLD -> Typeface.create("sans-serif-medium", Typeface.BOLD)
            SANS_LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            SANS_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            MONOSPACE -> Typeface.MONOSPACE
        }
    }

    fun toComposeFontFamily(): FontFamily {
        return when (this) {
            MONOSPACE -> FontFamily.Monospace
            else -> FontFamily.Default
        }
    }

    fun toComposeFontWeight(weight: Int = 550): FontWeight {
        return when (this) {
            OPPO_SANS -> FontWeight(weight.coerceIn(300, 700))
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
