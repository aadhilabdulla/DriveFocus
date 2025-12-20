package com.example.drivefocus.viewmodel

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed class DriveFocusAction {
    object Start : DriveFocusAction()
    object Stop : DriveFocusAction()
}
private const val PREFS_NAME = "drive_focus_state"
private const val KEY_IS_SERVICE_RUNNING = "is_service_running"
@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isDefaultCallerIdApp = MutableStateFlow(false)
    val isDefaultCallerIdApp: StateFlow<Boolean> = _isDefaultCallerIdApp.asStateFlow()

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == KEY_IS_SERVICE_RUNNING) {
                _isServiceRunning.value = sharedPreferences.getBoolean(KEY_IS_SERVICE_RUNNING, false)
            }
        }

    init {
        // Set the initial state for the service running status from SharedPreferences
        _isServiceRunning.value = prefs.getBoolean(KEY_IS_SERVICE_RUNNING, false)

        // Register the listener to react to background changes
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        // Set the initial state for the default caller ID role
        checkDefaultCallerIdStatus()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun checkDefaultCallerIdStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val app = getApplication<Application>()
            val roleManager = app.getSystemService(Context.ROLE_SERVICE) as RoleManager
            val isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            _isDefaultCallerIdApp.value = isRoleHeld
        } else {
            // On older versions, this role doesn't exist, so we can consider it "true"
            _isDefaultCallerIdApp.value = true
        }
    }
}