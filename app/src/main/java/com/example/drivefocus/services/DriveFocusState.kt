package com.example.drivefocus.services

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

// A simple singleton object to share state between our two services
object DriveFocusState {
    val isDriving = MutableStateFlow(false)
    val rejectedCallHistory = ConcurrentHashMap<String, Long>()
    const val EMERGENCY_TIME_WINDOW_MS = 60 * 1000 // 1 minute
}
