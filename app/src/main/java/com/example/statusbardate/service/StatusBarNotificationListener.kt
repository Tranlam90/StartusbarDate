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
        activeInstance = this
        NotificationTracker.setConnected(true)
        recalculateVisibleIcons()
    }

    override fun onListenerDisconnected() {
        if (activeInstance === this) {
            activeInstance = null
        }
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

    fun recalculateVisibleIcons() {
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
            // Bỏ qua thông báo nền của chính ứng dụng này
            if (sbn.packageName == packageName) continue

            val notif = sbn.notification ?: continue
            if (notif.smallIcon == null) continue

            // Bỏ qua thông báo mức tối thiểu (IMPORTANCE_MIN) vì không hiển thị icon trên thanh trạng thái
            if (rankingMap != null && rankingMap.getRanking(sbn.key, tempRanking)) {
                if (tempRanking.importance <= NotificationManager.IMPORTANCE_MIN) continue
            }

            // Gom nhóm các thông báo cùng nhóm thành 1 icon trên thanh trạng thái
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
        @Volatile
        private var activeInstance: StatusBarNotificationListener? = null

        fun refreshNow(context: Context) {
            val listener = activeInstance
            if (listener != null) {
                listener.recalculateVisibleIcons()
            } else {
                requestRebindIfNeeded(context)
            }
        }

        fun requestRebindIfNeeded(context: Context) {
            if (activeInstance == null || !NotificationTracker.state.value.isConnected) {
                runCatching {
                    requestRebind(ComponentName(context, StatusBarNotificationListener::class.java))
                }
            }
        }
    }
}
