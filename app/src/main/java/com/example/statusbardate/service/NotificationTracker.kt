package com.example.statusbardate.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StatusBarNotificationState(
    val isConnected: Boolean = false,
    val visibleIconCount: Int = 0
)

object NotificationTracker {
    private val _state = MutableStateFlow(StatusBarNotificationState())
    val state: StateFlow<StatusBarNotificationState> = _state.asStateFlow()

    fun setConnected(connected: Boolean) {
        _state.update {
            if (connected) it.copy(isConnected = true)
            else StatusBarNotificationState(isConnected = false, visibleIconCount = 0)
        }
    }

    fun updateIconCount(count: Int) {
        _state.update { it.copy(isConnected = true, visibleIconCount = count.coerceAtLeast(0)) }
    }
}
