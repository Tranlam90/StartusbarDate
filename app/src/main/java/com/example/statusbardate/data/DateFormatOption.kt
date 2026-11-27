package com.example.statusbardate.data

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
    AUTO("Tự động (Sáng / Tối)"),
    WHITE("Chữ trắng (Nền tối)"),
    BLACK("Chữ đen (Nền sáng)");

    companion object {
        fun fromName(name: String?): TextColorMode {
            return entries.find { it.name == name } ?: AUTO
        }
    }
}
