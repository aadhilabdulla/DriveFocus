package com.example.drivefocus.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.example.drivefocus.R
import android.os.Looper
import android.util.Log

private const val PREFS_NAME = "drive_focus_state"
private const val KEY_IS_SERVICE_RUNNING = "is_service_running"
private const val KEY_IS_DRIVING = "is_driving"

class DriveFocusService : Service() {

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.drivefocus.ACTION_STOP_SERVICE"
        // ACTION_SEND_SMS and EXTRA_PHONE_NUMBER are no longer needed here
    }

    // State for driving detection
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var isDriving = true
    private val DRIVING_SPEED_THRESHOLD_MPS = 5.0 // ~18 km/h or 11 mph

    private var highSpeedUpdates = 0
    private val REQUIRED_HIGH_SPEED_UPDATES = 3
    // State for emergency call override
    private var lowSpeedUpdates = 0
    private val REQUIRED_LOW_SPEED_UPDATES = 3
    private val EMERGENCY_TIME_WINDOW_MS = 60 * 1000 // 1 minute

    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {

            // FOR TESTING: Hardcode isDriving to true and write it to SharedPreferences.
//            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//            prefs.edit().putBoolean(KEY_IS_DRIVING, true).apply()
//            Log.d("DriveFocusService", "FORCING isDriving = true for testing.")

            locationResult.lastLocation?.let { location ->
                // Your robust driving detection logic
                if (location.speed > DRIVING_SPEED_THRESHOLD_MPS) {
                    highSpeedUpdates++
                    lowSpeedUpdates = 0
                    if (highSpeedUpdates >= REQUIRED_HIGH_SPEED_UPDATES) {
                        isDriving = true
                    }
                } else {
                    lowSpeedUpdates++
                    highSpeedUpdates = 0
                    if (lowSpeedUpdates >= REQUIRED_LOW_SPEED_UPDATES) {
                        isDriving = false
                    }
                }

                // Write the current driving state to SharedPreferences
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_IS_DRIVING, isDriving).apply()

                Log.d("DriveFocusService", "Speed: ${location.speed} m/s | HighSpeed: $highSpeedUpdates | LowSpeed: $lowSpeedUpdates | isDriving: $isDriving")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
    }

// In DriveFocusService.kt

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Only check for the stop action
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d("DriveFocusService", "Stop command received. Shutting down service.")
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // This is now only for the initial start of the service from the UI
        try {
            createNotificationChannel()
            val notification = createNotification()
            startForeground(1, notification)

            // Write the service running state to SharedPreferences
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_IS_SERVICE_RUNNING, true).apply()

            startLocationUpdates()
        } catch (e: Exception) {
            Log.e("DriveFocusService", "Error starting foreground service", e)
            stopSelf()
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission") // We check for permissions before starting service
    private fun startLocationUpdates() {        // Use the correct builder pattern from Google Play Services location API
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).apply {
            setMinUpdateIntervalMillis(3000)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }


    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear all state from SharedPreferences when the service is fully stopped
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_SERVICE_RUNNING, false)
            .putBoolean(KEY_IS_DRIVING, false)
            .apply()
        stopLocationUpdates()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "DriveFocus Channel"
            val descriptionText = "Notifications for DriveFocus service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("DRIVEFOCUS_CHANNEL_ID", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // You need to provide an icon for the notification.
        // Replace 'ic_drive_focus_notification' with your actual notification icon in the drawable folder.
        val notification = NotificationCompat.Builder(this, "DRIVEFOCUS_CHANNEL_ID")
            .setContentTitle("DriveFocus is Active")
            .setContentText("Your driving is being monitored.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your own icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return notification
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding, so return null
        return null
    }
}
