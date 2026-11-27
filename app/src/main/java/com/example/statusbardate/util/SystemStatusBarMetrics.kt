package com.example.statusbardate.util

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.roundToInt

data class ClockMatchMetrics(
    val statusBarHeightPx: Int,
    val clockTextSizePx: Float,
    val clockTextSizeSp: Float,
    val recommendedOffsetX: Int,
    val sourceLabel: String
)

object SystemStatusBarMetrics {

    fun measure(context: Context): ClockMatchMetrics {
        val dm = context.resources.displayMetrics
        val density = dm.density.coerceAtLeast(1f)
        val scaledDensity = dm.scaledDensity.coerceAtLeast(1f)

        val statusBarHeight = detectStatusBarHeightPx(context, density)
        val (clockPx, source) = detectClockTextSizePx(context, density, statusBarHeight)
        val clockSp = (clockPx / scaledDensity * 10f).roundToInt() / 10f

        // Căn vị trí X mặc định ngay bên phải đồng hồ hệ thống (~62dp từ mép trái màn hình)
        val recommendedX = (62f * density).roundToInt()

        return ClockMatchMetrics(
            statusBarHeightPx = statusBarHeight,
            clockTextSizePx = clockPx,
            clockTextSizeSp = clockSp,
            recommendedOffsetX = recommendedX,
            sourceLabel = source
        )
    }

    private fun detectStatusBarHeightPx(context: Context, density: Float): Int {
        // 1. Ưu tiên WindowInsets thực tế của màn hình (chính xác tuyệt đối theo camera đục lỗ)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            val topInset = runCatching {
                wm?.maximumWindowMetrics
                    ?.windowInsets
                    ?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                    ?.top ?: 0
            }.getOrDefault(0)
            if (topInset > 0) return topInset
        }

        // 2. Đọc từ tài nguyên nội bộ của SystemUI
        runCatching {
            val sysUiRes = context.packageManager.getResourcesForApplication("com.android.systemui")
            val id = sysUiRes.getIdentifier("status_bar_height", "dimen", "com.android.systemui")
            if (id != 0) {
                val px = sysUiRes.getDimensionPixelSize(id)
                if (px > 0) return px
            }
        }

        // 3. Đọc từ tài nguyên khung Android
        val androidResId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (androidResId != 0) {
            val px = context.resources.getDimensionPixelSize(androidResId)
            if (px > 0) return px
        }

        return (36f * density).roundToInt()
    }

    private fun detectClockTextSizePx(
        context: Context,
        density: Float,
        statusBarHeightPx: Int
    ): Pair<Float, String> {
        // Đọc trực tiếp kích thước chữ đồng hồ từ com.android.systemui (AOSP & ColorOS/Oplus)
        val dimenCandidates = listOf(
            "status_bar_clock_size",
            "oplus_status_bar_clock_size",
            "status_bar_time_size",
            "clock_text_size"
        )

        runCatching {
            val sysUiRes = context.packageManager.getResourcesForApplication("com.android.systemui")
            for (name in dimenCandidates) {
                val id = sysUiRes.getIdentifier(name, "dimen", "com.android.systemui")
                if (id != 0) {
                    val px = sysUiRes.getDimension(id)
                    if (px in (10f * density)..(18f * density)) {
                        return Pair(px, "SystemUI ($name)")
                    }
                }
            }
        }

        // Dự phòng theo chuẩn thanh trạng thái Android/ColorOS (~14dp)
        val fallbackPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            14f,
            context.resources.displayMetrics
        )
        val safePx = fallbackPx.coerceIn(11f * density, (statusBarHeightPx * 0.48f).coerceAtLeast(12f * density))
        return Pair(safePx, "Chuẩn hệ thống (14dp)")
    }
}
