package com.example.statusbardate.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class StatusBarNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationTracker.setConnected(true)
        recalculateVisibleIcons()
    }

    override fun onListenerDisconnected() {
        NotificationTracker.setConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recalculateVisibleIcons()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recalculateVisibleIcons()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        recalculateVisibleIcons()
    }

    private fun recalculateVisibleIcons() {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        val rankingMap = try {
            currentRanking
        } catch (_: Exception) {
            null
        }

        val tempRanking = Ranking()
        val seenGroups = HashSet<String>()
        var visibleCount = 0

        for (sbn in active) {
            if (sbn == null) continue
            // Bỏ qua thông báo nền của chính ứng dụng này (IMPORTANCE_MIN không hiện icon)
            if (sbn.packageName == packageName) continue

            val notif = sbn.notification ?: continue

            // Kiểm tra mức độ quan trọng & trạng thái ẩn trên thanh trạng thái
            if (rankingMap != null && rankingMap.getRanking(sbn.key, tempRanking)) {
                if (tempRanking.importance <= NotificationManager.IMPORTANCE_MIN) continue
                if (tempRanking.isAmbient) continue
            }

            // Các thông báo cùng nhóm trên thanh trạng thái được gom thành 1 icon duy nhất
            if (sbn.isGroup) {
                val groupId = "${sbn.packageName}:${sbn.groupKey}"
                if (seenGroups.add(groupId)) {
                    visibleCount++
                }
            } else {
                if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                visibleCount++
            }
        }

        NotificationTracker.updateIconCount(visibleCount)
    }

    companion object {
        fun requestRebindIfNeeded(context: Context) {
            if (!NotificationTracker.state.value.isConnected) {
                runCatching {
                    requestRebind(ComponentName(context, StatusBarNotificationListener::class.java))
                }
            }
        }
    }
}
