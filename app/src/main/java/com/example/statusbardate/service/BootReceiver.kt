package com.example.statusbardate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.statusbardate.data.PrefsManager
import com.example.statusbardate.util.PermissionHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = PrefsManager.getInstance(context)
            if (prefs.config.value.isEnabled && Settings.canDrawOverlays(context)) {
                DateOverlayService.start(context)
                if (PermissionHelper.hasNotificationListenerPermission(context)) {
                    StatusBarNotificationListener.requestRebindIfNeeded(context)
                }
            }
        }
    }
}
