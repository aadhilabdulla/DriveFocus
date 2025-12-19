package com.example.drivefocus.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SmsManager
import androidx.annotation.RequiresApi
import android.util.Log

// Define all SharedPreferences keys used by both services
private const val PREFS_NAME = "drive_focus_state"
private const val KEY_IS_SERVICE_RUNNING = "is_service_running"
private const val KEY_IS_DRIVING = "is_driving"
private const val KEY_REJECTED_CALL_HISTORY_PREFIX = "rejected_call_" // Prefix for storing call times
private const val EMERGENCY_TIME_WINDOW_MS = 60 * 1000 // 1 minute

@RequiresApi(Build.VERSION_CODES.Q)
class CallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d("CallScreeningService", "-> onScreenCall TRIGGERED.")

        // 1. READ state ONLY from SharedPreferences
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isServiceRunning = prefs.getBoolean(KEY_IS_SERVICE_RUNNING, false)

        if (!isServiceRunning) {
            Log.w("CallScreeningService", "-> Service is not running (read from Prefs). ALLOWING call.")
            return
        }
        Log.d("CallScreeningService", "-> Service is running. Proceeding.")

        val isDriving = prefs.getBoolean(KEY_IS_DRIVING, false)

        if (!isDriving) {
            Log.w("CallScreeningService", "-> isDriving is FALSE (read from Prefs). ALLOWING call.")
            return
        }
        Log.d("CallScreeningService", "-> isDriving is TRUE. Proceeding.")

        val phoneNumber = callDetails.handle.schemeSpecificPart
        Log.d("CallScreeningService", "-> Screening call from: $phoneNumber")

        if (isEmergencyCallback(applicationContext, phoneNumber)) {
            Log.w("CallScreeningService", "-> Emergency callback detected. ALLOWING call.")
            return
        }
        Log.d("CallScreeningService", "-> Not an emergency callback. Proceeding to reject.")

        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        Log.d("CallScreeningService", "-> Built the REJECT response. About to respond to system...")
        respondToCall(callDetails, response)
        Log.d("CallScreeningService", "-> SUCCEEDED: respondToCall was executed.")

        // 2. SEND THE SMS and SAVE HISTORY from *HERE*
        sendRejectionSms(applicationContext, phoneNumber)

        val key = "$KEY_REJECTED_CALL_HISTORY_PREFIX$phoneNumber"
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        Log.d("CallScreeningService", "-> Saved rejection time for $phoneNumber.")
    }

    // 4. REWRITE the emergency callback to use SharedPreferences
    private fun isEmergencyCallback(context: Context, phoneNumber: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$KEY_REJECTED_CALL_HISTORY_PREFIX$phoneNumber"
        val lastCallTime = prefs.getLong(key, -1)

        if (lastCallTime == -1L) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val isEmergency = (currentTime - lastCallTime) < EMERGENCY_TIME_WINDOW_MS

        if (isEmergency) {
            prefs.edit().remove(key).apply()
        }
        return isEmergency
    }
    @SuppressLint("MissingPermission")
    private fun sendRejectionSms(context: Context, phoneNumber: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val message = "Sorry I am driving. Call me again if it's an emergency."
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("CallScreeningService", "-> SMS sent successfully to $phoneNumber.")
        } catch (e: Exception) {
            Log.e("CallScreeningService", "-> Failed to send SMS", e)
        }
    }
}
