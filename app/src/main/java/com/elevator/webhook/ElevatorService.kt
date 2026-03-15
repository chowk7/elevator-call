package com.elevator.webhook

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ElevatorService : Service() {
    companion object {
        private const val TAG = "ElevatorService"
        private const val CHANNEL_ID = "elevator_service"
        private const val NOTIFICATION_ID = 1
        private const val DELAY_BEFORE_STATUS_CHECK = 2000L // 2 seconds delay
    }

    private var ttsManager: TextToSpeechManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ttsManager = TextToSpeechManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        callElevatorAndCheckStatus()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun callElevatorAndCheckStatus() {
        Thread {
            try {
                // Step 1: Call elevator
                Log.d(TAG, "Calling elevator...")
                val callResult = ElevatorAPIClient.callElevator()

                if (callResult.isSuccess) {
                    Log.d(TAG, "Elevator called successfully")
                    addLog("호출 성공")

                    // Wait before checking status
                    Thread.sleep(DELAY_BEFORE_STATUS_CHECK)

                    // Step 2: Get elevator status
                    Log.d(TAG, "Checking elevator status...")
                    val statusResult = ElevatorAPIClient.getElevatorStatus()

                    if (statusResult.isSuccess) {
                        val statusXml = statusResult.getOrNull() ?: ""
                        Log.d(TAG, "Status response: $statusXml")

                        // Step 3: Parse floor from response
                        val floor = XMLParser.extractFloor(statusXml)
                        if (floor != null) {
                            Log.d(TAG, "Current floor: $floor")
                            addLog("현재 층: $floor")

                            // Step 4: Speak the result
                            val message = getString(R.string.elevator_called, floor)
                            ttsManager?.speak(message)
                            Log.d(TAG, "Speaking: $message")
                        } else {
                            Log.e(TAG, "Could not parse floor from response")
                            addLog("층 정보 파싱 실패")
                        }
                    } else {
                        Log.e(TAG, "Failed to get status: ${statusResult.exceptionOrNull()}")
                        addLog("상태 확인 실패")
                    }
                } else {
                    Log.e(TAG, "Failed to call elevator: ${callResult.exceptionOrNull()}")
                    addLog("호출 실패")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in callElevatorAndCheckStatus", e)
                addLog("오류: ${e.message}")
            } finally {
                // Stop the service after completing the task
                stopSelf()
            }
        }.start()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Elevator Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message"

        val prefs = getSharedPreferences("elevator_logs", MODE_PRIVATE)
        val logs = prefs.getStringSet("logs", LinkedHashSet()) ?: LinkedHashSet()

        val newLogs = logs.toMutableSet()
        newLogs.add(logMessage)

        // Keep only last 50 logs
        if (newLogs.size > 50) {
            newLogs.remove(newLogs.first())
        }

        prefs.edit().putStringSet("logs", newLogs).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager?.shutdown()
    }
}
