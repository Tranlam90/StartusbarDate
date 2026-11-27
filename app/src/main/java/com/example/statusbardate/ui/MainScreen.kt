package com.example.statusbardate.ui

import android.Manifest
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.statusbardate.data.DateFormatOption
import com.example.statusbardate.data.FontStyleOption
import com.example.statusbardate.data.OemFontResolver
import com.example.statusbardate.data.PrefsManager
import com.example.statusbardate.data.TextColorMode
import com.example.statusbardate.service.DateOverlayService
import com.example.statusbardate.service.NotificationTracker
import com.example.statusbardate.service.StatusBarNotificationListener
import com.example.statusbardate.util.InstalledAppInfo
import com.example.statusbardate.util.PermissionHelper
import com.example.statusbardate.util.SystemStatusBarMetrics
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefsManager = remember { PrefsManager.getInstance(context) }
    val config by prefsManager.config.collectAsState()
    val notifState by NotificationTracker.state.collectAsState()
    val clockMetrics = remember { SystemStatusBarMetrics.measure(context) }

    var hasOverlayPerm by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var hasNotifPerm by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var isBatteryIgnored by remember {
        mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context))
    }
    var hasUsagePerm by remember {
        mutableStateOf(PermissionHelper.hasUsageStatsPermission(context))
    }
    var hasNotifListenerPerm by remember {
        mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context))
    }

    var showLightAppsDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val savedPath = OemFontResolver.loadCustomFontFromUri(context, uri)
            if (savedPath != null) {
                prefsManager.setCustomFontPath(savedPath)
                Toast.makeText(context, "Đã nạp font tùy chỉnh thành công", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Không đọc được file font này", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPerm = PermissionHelper.canDrawOverlays(context)
                hasNotifPerm = PermissionHelper.hasNotificationPermission(context)
                isBatteryIgnored = PermissionHelper.isIgnoringBatteryOptimizations(context)
                hasUsagePerm = PermissionHelper.hasUsageStatsPermission(context)
                hasNotifListenerPerm = PermissionHelper.hasNotificationListenerPermission(context)

                if (hasNotifListenerPerm) {
                    StatusBarNotificationListener.refreshNow(context)
                }

                if (config.isEnabled && hasOverlayPerm) {
                    DateOverlayService.start(context)
                } else if (config.isEnabled && !hasOverlayPerm) {
                    prefsManager.setEnabled(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showLightAppsDialog) {
        LightAppsPickerDialog(
            selectedPackages = config.lightAppPackages,
            onTogglePackage = { prefsManager.toggleLightAppPackage(it) },
            onResetDefaults = { prefsManager.resetDefaultLightApps() },
            onDismiss = { showLightAppsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ngày trên Thanh trạng thái", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Công tắc chính
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (config.isEnabled && hasOverlayPerm) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (config.isEnabled && hasOverlayPerm) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.ErrorOutline
                            },
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when {
                                    !hasOverlayPerm -> "Chưa cấp quyền hiển thị trên ứng dụng khác"
                                    config.isEnabled -> "Đang hoạt động"
                                    else -> "Đã tắt"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tự né icon thông báo & đổi màu theo ứng dụng",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Switch(
                        checked = config.isEnabled && hasOverlayPerm,
                        onCheckedChange = { wantEnable ->
                            if (wantEnable) {
                                if (!hasOverlayPerm) {
                                    PermissionHelper.openOverlayPermissionSettings(context)
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    prefsManager.setEnabled(true)
                                    DateOverlayService.start(context)
                                }
                            } else {
                                prefsManager.setEnabled(false)
                                DateOverlayService.stop(context)
                            }
                        }
                    )
                }
            }

            // 2. Xem trước hiển thị
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Xem trước trên thanh trạng thái",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    val formattedDate = config.formatOption.format()

                    val previewTypeface = remember(
                        config.fontStyle,
                        config.fontWeight,
                        config.selectedOemFontId,
                        config.customFontPath
                    ) {
                        config.fontStyle.toAndroidTypeface(
                            weight = config.fontWeight,
                            selectedOemFontId = config.selectedOemFontId,
                            customFontPath = config.customFontPath
                        )
                    }
                    val previewWeight = if (
                        config.fontStyle == FontStyleOption.OPPO_SANS ||
                        config.fontStyle == FontStyleOption.CUSTOM_FILE
                    ) config.fontWeight else null

                    StatusBarPreviewRow(
                        timeText = currentTime,
                        dateText = formattedDate,
                        bgColor = Color(0xFF141414),
                        fgColor = android.graphics.Color.WHITE,
                        fontSizeSp = if (config.autoMatchClock) clockMetrics.clockTextSizeSp else config.textSizeSp,
                        androidTypeface = previewTypeface,
                        variationWeight = previewWeight
                    )
                    StatusBarPreviewRow(
                        timeText = currentTime,
                        dateText = formattedDate,
                        bgColor = Color(0xFFF2F2F2),
                        fgColor = android.graphics.Color.parseColor("#1C1B1F"),
                        fontSizeSp = if (config.autoMatchClock) clockMetrics.clockTextSizeSp else config.textSizeSp,
                        androidTypeface = previewTypeface,
                        variationWeight = previewWeight
                    )
                }
            }

            // 3. Tự động dịch sang phải né icon thông báo
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tự dịch sang phải né icon thông báo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (hasNotifListenerPerm && notifState.isConnected) {
                                    val clamped = notifState.visibleIconCount.coerceIn(0, config.maxDodgeIcons)
                                    val shift = if (config.autoDodgeNotifications) clamped * config.iconStepPx else 0
                                    "Đang thấy ${notifState.visibleIconCount} icon → dịch phải +$shift px"
                                } else if (hasNotifListenerPerm) {
                                    "Đã cấp quyền, đang kết nối dịch vụ đếm icon..."
                                } else {
                                    "Cần cấp quyền Truy cập thông báo để đếm icon"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.autoDodgeNotifications,
                            onCheckedChange = { wantDodge ->
                                if (wantDodge && !hasNotifListenerPerm) {
                                    PermissionHelper.openNotificationListenerSettings(context)
                                }
                                prefsManager.setAutoDodgeNotifications(wantDodge)
                            }
                        )
                    }

                    if (config.autoDodgeNotifications) {
                        if (!hasNotifListenerPerm) {
                            Button(
                                onClick = { PermissionHelper.openNotificationListenerSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cấp quyền Truy cập thông báo (Né icon)")
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Text(
                                    text = "Bắt buộc trên OPPO (ColorOS): Bấm nút bên dưới → chạm dấu 3 chấm (⋮) góc trên bên phải → chọn «Cho phép cài đặt bị hạn chế», sau đó mới bật được quyền Truy cập thông báo ở nút trên.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(
                                    onClick = { PermissionHelper.openAppDetailsSettings(context) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Bước 1: Mở Thông tin ứng dụng (Bật cài đặt bị hạn chế)")
                                }
                            }
                        }

                        Text(
                            text = "Khoảng cách dịch mỗi 1 icon: ${config.iconStepPx} px",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = config.iconStepPx.toFloat(),
                            onValueChange = { prefsManager.setIconStepPx(it.roundToInt()) },
                            valueRange = 28f..100f
                        )

                        Text(
                            text = "Số icon dịch tối đa (tránh đụng camera): ${config.maxDodgeIcons} icon",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = config.maxDodgeIcons.toFloat(),
                            onValueChange = { prefsManager.setMaxDodgeIcons(it.roundToInt()) },
                            valueRange = 1f..6f,
                            steps = 4
                        )
                    }
                }
            }

            // 4. Định dạng ngày tháng & Kiểu font
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Định dạng ngày tháng",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateFormatOption.entries.forEach { option ->
                            FilterChip(
                                selected = config.formatOption == option,
                                onClick = { prefsManager.setFormat(option) },
                                label = { Text(option.label) }
                            )
                        }
                    }

                    Text(
                        text = "Kiểu chữ (Font khớp với đồng hồ máy)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontStyleOption.entries.forEach { style ->
                            FilterChip(
                                selected = config.fontStyle == style,
                                onClick = { prefsManager.setFontStyle(style) },
                                label = { Text(style.label) }
                            )
                        }
                    }

                    if (config.fontStyle == FontStyleOption.OPPO_SANS ||
                        config.fontStyle == FontStyleOption.CUSTOM_FILE
                    ) {
                        val oemCandidates = remember { OemFontResolver.getAllCandidates() }
                        val activeOem = remember(config.selectedOemFontId) {
                            OemFontResolver.resolveById(config.selectedOemFontId)
                        }
                        Text(
                            text = if (config.fontStyle == FontStyleOption.CUSTOM_FILE && config.customFontPath != null) {
                                "Đang dùng file font riêng • Độ đậm: ${config.fontWeight}"
                            } else {
                                "Font hệ thống đang nạp: ${activeOem.sourceName} • Độ đậm: ${config.fontWeight}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (config.fontStyle == FontStyleOption.OPPO_SANS && oemCandidates.size > 1) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                oemCandidates.take(6).forEach { cand ->
                                    FilterChip(
                                        selected = activeOem.id == cand.id,
                                        onClick = { prefsManager.setSelectedOemFontId(cand.id) },
                                        label = { Text(cand.sourceName) }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { prefsManager.setFontWeight(450) }) {
                                Text("Mảnh (450)")
                            }
                            OutlinedButton(onClick = { prefsManager.setFontWeight(550) }) {
                                Text("Chuẩn OPPO (550)")
                            }
                            OutlinedButton(onClick = { prefsManager.setFontWeight(620) }) {
                                Text("Đậm (620)")
                            }
                        }
                        Slider(
                            value = config.fontWeight.toFloat(),
                            onValueChange = {
                                val snapped = ((it / 25f).roundToInt() * 25).coerceIn(300, 700)
                                prefsManager.setFontWeight(snapped)
                            },
                            valueRange = 300f..700f
                        )
                    }

                    OutlinedButton(
                        onClick = { fontPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Chọn file font (.ttf / .otf) từ bộ nhớ máy")
                    }
                }
            }

            // 5. Màu chữ & Tự động đổi màu theo ứng dụng
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Màu chữ hiển thị",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextColorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.colorMode == mode,
                                onClick = { prefsManager.setColorMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    if (config.colorMode == TextColorMode.AUTO) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tự đổi chữ đen khi mở app nền sáng",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Facebook, Zalo, Shopee, Gmail, Ngân hàng...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = config.autoDetectLightApps,
                                onCheckedChange = { wantAuto ->
                                    if (wantAuto && !hasUsagePerm) {
                                        PermissionHelper.openUsageAccessSettings(context)
                                    }
                                    prefsManager.setAutoDetectLightApps(wantAuto)
                                }
                            )
                        }

                        if (config.autoDetectLightApps) {
                            if (!hasUsagePerm) {
                                Button(
                                    onClick = { PermissionHelper.openUsageAccessSettings(context) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Visibility, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cấp quyền nhận diện ứng dụng đang mở")
                                }
                            }

                            OutlinedButton(
                                onClick = { showLightAppsDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Apps, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Chọn ứng dụng nền sáng (${config.lightAppPackages.size} app)")
                            }
                        }
                    }
                }
            }

            // 6. Tự khớp ngang hàng & kích cỡ với đồng hồ hệ thống
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tự ngang hàng & bằng cỡ chữ đồng hồ",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tự căn giữa thanh trạng thái (${clockMetrics.statusBarHeightPx} px) • Cỡ chữ ${clockMetrics.clockTextSizeSp} sp (${clockMetrics.sourceLabel})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.autoMatchClock,
                            onCheckedChange = { prefsManager.setAutoMatchClock(it) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            prefsManager.setAutoMatchClock(true)
                            prefsManager.setVerticalNudgePx(0)
                            prefsManager.setOffsetX(clockMetrics.recommendedOffsetX)
                        }) {
                            Text("Cạnh giờ (Tự động)")
                        }
                        OutlinedButton(onClick = {
                            prefsManager.setAutoMatchClock(true)
                            prefsManager.setVerticalNudgePx(0)
                            prefsManager.setOffsetX(480)
                        }) {
                            Text("Giữa màn hình")
                        }
                    }

                    Text("Khoảng cách ngang từ mép trái (X): ${config.offsetX} px", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = config.offsetX.toFloat(),
                        onValueChange = { prefsManager.setOffsetX(it.roundToInt()) },
                        valueRange = 0f..900f
                    )

                    if (config.autoMatchClock) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Vi chỉnh độ cao (±12 px): ${config.verticalNudgePx} px",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (config.verticalNudgePx != 0) {
                                TextButton(onClick = { prefsManager.setVerticalNudgePx(0) }) {
                                    Text("Về 0 px")
                                }
                            }
                        }
                        Slider(
                            value = config.verticalNudgePx.toFloat(),
                            onValueChange = { prefsManager.setVerticalNudgePx(it.roundToInt()) },
                            valueRange = -12f..12f
                        )
                    } else {
                        Text("Vị trí dọc thủ công (Y): ${config.offsetY} px", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = config.offsetY.toFloat(),
                            onValueChange = { prefsManager.setOffset(config.offsetX, it.roundToInt()) },
                            valueRange = -20f..140f
                        )

                        Text(
                            "Cỡ chữ thủ công: ${String.format(Locale.US, "%.1f", config.textSizeSp)} sp",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = config.textSizeSp,
                            onValueChange = { prefsManager.setTextSize(it) },
                            valueRange = 10f..17f
                        )
                    }
                }
            }

            // 7. Quyền hệ thống & Độ ổn định
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tổng hợp quyền hệ thống",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Button(
                        onClick = { PermissionHelper.openOverlayPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Layers, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasOverlayPerm) "1. Hiển thị trên ứng dụng khác: Đã cấp"
                            else "1. Cấp quyền hiển thị trên ứng dụng khác"
                        )
                    }

                    OutlinedButton(
                        onClick = { PermissionHelper.openNotificationListenerSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasNotifListenerPerm) "2. Truy cập thông báo (Né icon): Đã cấp"
                            else "2. Cấp quyền Truy cập thông báo (Né icon)"
                        )
                    }

                    OutlinedButton(
                        onClick = { PermissionHelper.openUsageAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasUsagePerm) "3. Nhận diện app nền sáng: Đã cấp"
                            else "3. Cấp quyền nhận diện app (Đổi màu Facebook)"
                        )
                    }

                    OutlinedButton(
                        onClick = { PermissionHelper.openBatteryOptimizationSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isBatteryIgnored) "4. Tối ưu hóa pin: Đã tắt (Ổn định)"
                            else "4. Tắt tối ưu hóa pin (Tránh bị hệ thống đóng)"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LightAppsPickerDialog(
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val installedApps = remember { PermissionHelper.getLaunchableApps(context) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, searchQuery, selectedPackages) {
        installedApps
            .filter { app ->
                searchQuery.isBlank() ||
                    app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { selectedPackages.contains(it.packageName) }
                    .thenBy { it.label.lowercase() }
            )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ứng dụng nền sáng (Chữ đen)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Tìm tên ứng dụng (vd: Facebook)...") }
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val checked = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePackage(app.packageName) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onTogglePackage(app.packageName) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Xong")
            }
        },
        dismissButton = {
            TextButton(onClick = onResetDefaults) {
                Text("Mặc định")
            }
        }
    )
}

@Composable
private fun StatusBarPreviewRow(
    timeText: String,
    dateText: String,
    bgColor: Color,
    fgColor: Int,
    fontSizeSp: Float,
    androidTypeface: Typeface,
    variationWeight: Int?
) {
    val composeFgColor = Color(fgColor)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeText,
                    color = composeFgColor,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            includeFontPadding = false
                            fontFeatureSettings = "tnum"
                        }
                    },
                    update = { tv ->
                        tv.text = dateText
                        tv.setTextColor(fgColor)
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                        runCatching { tv.fontVariationSettings = null }
                        tv.setTypeface(androidTypeface, Typeface.NORMAL)
                        if (variationWeight != null) {
                            runCatching { tv.fontVariationSettings = "'wght' $variationWeight" }
                        }
                    }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = composeFgColor,
                    modifier = Modifier.size(14.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.SignalCellularAlt,
                    contentDescription = null,
                    tint = composeFgColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
