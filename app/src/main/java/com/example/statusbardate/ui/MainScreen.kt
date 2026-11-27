package com.example.statusbardate.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.statusbardate.data.DateFormatOption
import com.example.statusbardate.data.PrefsManager
import com.example.statusbardate.data.TextColorMode
import com.example.statusbardate.service.DateOverlayService
import com.example.statusbardate.util.PermissionHelper
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

    var hasOverlayPerm by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var hasNotifPerm by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var isBatteryIgnored by remember {
        mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPerm = PermissionHelper.canDrawOverlays(context)
                hasNotifPerm = PermissionHelper.hasNotificationPermission(context)
                isBatteryIgnored = PermissionHelper.isIgnoringBatteryOptimizations(context)

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
                                text = "Tự cập nhật khi sang ngày mới & đổi múi giờ",
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
                    val currentTime = remember {
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    }
                    val formattedDate = config.formatOption.format()

                    StatusBarPreviewRow(
                        timeText = currentTime,
                        dateText = formattedDate,
                        bgColor = Color(0xFF141414),
                        fgColor = Color.White,
                        fontSizeSp = config.textSizeSp
                    )
                    StatusBarPreviewRow(
                        timeText = currentTime,
                        dateText = formattedDate,
                        bgColor = Color(0xFFF2F2F2),
                        fgColor = Color(0xFF1C1B1F),
                        fontSizeSp = config.textSizeSp
                    )
                }
            }

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
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Căn chỉnh vị trí cạnh đồng hồ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { prefsManager.setOffset(165, 14) }) {
                            Text("Góc trái (cạnh giờ)")
                        }
                        OutlinedButton(onClick = { prefsManager.setOffset(480, 14) }) {
                            Text("Giữa màn hình")
                        }
                    }

                    Text("Vị trí ngang (X): ${config.offsetX} px", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = config.offsetX.toFloat(),
                        onValueChange = { prefsManager.setOffset(it.roundToInt(), config.offsetY) },
                        valueRange = 0f..900f
                    )

                    Text("Vị trí dọc (Y): ${config.offsetY} px", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = config.offsetY.toFloat(),
                        onValueChange = { prefsManager.setOffset(config.offsetX, it.roundToInt()) },
                        valueRange = -20f..140f
                    )

                    Text(
                        "Cỡ chữ: ${String.format(Locale.US, "%.1f", config.textSizeSp)} sp",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = config.textSizeSp,
                        onValueChange = { prefsManager.setTextSize(it) },
                        valueRange = 10f..17f
                    )
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Quyền hệ thống & Độ ổn định",
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
                            if (hasOverlayPerm) "Quyền hiển thị trên ứng dụng khác: Đã cấp"
                            else "1. Cấp quyền hiển thị trên ứng dụng khác"
                        )
                    }

                    OutlinedButton(
                        onClick = { PermissionHelper.openBatteryOptimizationSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isBatteryIgnored) "Tối ưu hóa pin: Đã tắt (Ổn định)"
                            else "2. Tắt tối ưu hóa pin (Tránh bị hệ thống đóng)"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBarPreviewRow(
    timeText: String,
    dateText: String,
    bgColor: Color,
    fgColor: Color,
    fontSizeSp: Float
) {
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
                    color = fgColor,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dateText,
                    color = fgColor,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(14.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.SignalCellularAlt,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
