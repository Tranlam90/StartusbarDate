package com.example.statusbardate.data

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

enum class FontStyleOption(val label: String) {
    SYSTEM_REGULAR("Mặc định máy (Thường)"),
    SYSTEM_MEDIUM("Mặc định máy (Vừa)"),
    SYSTEM_SEMIBOLD("Mặc định máy (Đậm)"),
    SANS_LIGHT("Nét mảnh (Light)"),
    SANS_CONDENSED("Chữ gọn (Condensed)"),
    MONOSPACE("Số đều (Monospace)");

    fun toAndroidTypeface(): Typeface {
        return when (this) {
            SYSTEM_REGULAR -> Typeface.create(Typeface.DEFAULT, 400, false)
            SYSTEM_MEDIUM -> Typeface.create(Typeface.DEFAULT, 500, false)
            SYSTEM_SEMIBOLD -> Typeface.create(Typeface.DEFAULT, 600, false)
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

    fun toComposeFontWeight(): FontWeight {
        return when (this) {
            SYSTEM_REGULAR, SANS_CONDENSED -> FontWeight.Normal
            SYSTEM_MEDIUM -> FontWeight.Medium
            SYSTEM_SEMIBOLD -> FontWeight.SemiBold
            SANS_LIGHT -> FontWeight.Light
            MONOSPACE -> FontWeight.Normal
        }
    }

    companion object {
        fun fromName(name: String?): FontStyleOption {
            return entries.find { it.name == name } ?: SYSTEM_MEDIUM
        }
    }
}
