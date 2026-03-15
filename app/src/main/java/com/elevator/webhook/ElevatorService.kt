package com.elevator.webhook

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ElevatorService : Service() {
    companion object {
        private const val TAG = "ElevatorService"
        private const val CHANNEL_ID = "elevator_service"
        private const val NOTIFICATION_ID = 1
        private const val DELAY_BEFORE_STATUS_CHECK = 2000L
        const val ACTION_CALL_ELEVATOR = "com.elevator.webhook.CALL_ELEVATOR"
        const val ACTION_CHECK_STATUS = "com.elevator.webhook.CHECK_STATUS"
    }

    private var ttsManager: TextToSpeechManager? = null
    private var wasCharging = false
    private var powerReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ttsManager = TextToSpeechManager(this)
        registerPowerReceiver()
        updateChargingState()
        Log.d(TAG, "Service created, monitoring charger state")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("충전기 모니터링 중"))

        when (intent?.action) {
            ACTION_CALL_ELEVATOR -> callElevatorAndCheckStatus()
            ACTION_CHECK_STATUS -> checkStatusOnly()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerPowerReceiver() {
        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        Log.d(TAG, "Charger connected")
                        wasCharging = true
                        updateNotification("충전기 연결됨")
                        addLog("충전기 연결됨")
                        broadcastChargerState(true)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        Log.d(TAG, "Charger disconnected")
                        if (wasCharging) {
                            Log.d(TAG, "Transition: charging -> not charging, calling elevator")
                            addLog("충전기 해제 → 엘레베이터 호출")
                            callElevatorAndCheckStatus()
                        }
                        wasCharging = false
                        updateNotification("충전기 미연결")
                        broadcastChargerState(false)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun updateChargingState() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        val plugged = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        wasCharging = plugged > 0
        Log.d(TAG, "Initial charging state: $wasCharging")
    }

    private fun broadcastChargerState(isCharging: Boolean) {
        val intent = Intent("com.elevator.webhook.CHARGER_STATE_CHANGED")
        intent.putExtra("is_charging", isCharging)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    fun callElevatorAndCheckStatus() {
        Thread {
            try {
                Log.d(TAG, "Calling elevator...")
                addLog("엘레베이터 호출 중...")
                val callResult = ElevatorAPIClient.callElevator()

                if (callResult.isSuccess) {
                    Log.d(TAG, "Elevator called successfully")
                    addLog("호출 성공")

                    Thread.sleep(DELAY_BEFORE_STATUS_CHECK)

                    Log.d(TAG, "Checking elevator status...")
                    val statusResult = ElevatorAPIClient.getElevatorStatus()

                    if (statusResult.isSuccess) {
                        val statusXml = statusResult.getOrNull() ?: ""
                        Log.d(TAG, "Status response: $statusXml")

                        val floor = XMLParser.extractFloor(statusXml)
                        if (floor != null) {
                            Log.d(TAG, "Current floor: $floor")
                            addLog("현재 층: $floor")

                            val message = "호출하였습니다. 현재 ${floor}층 입니다."
                            ttsManager?.speak(message)
                            Log.d(TAG, "Speaking: $message")

                            broadcastFloorUpdate(floor)
                        } else {
                            Log.e(TAG, "Could not parse floor from response")
                            addLog("층 정보 파싱 실패")
                        }
                    } else {
                        Log.e(TAG, "Failed to get status: ${statusResult.exceptionOrNull()}")
                        addLog("상태 확인 실패: ${statusResult.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.e(TAG, "Failed to call elevator: ${callResult.exceptionOrNull()}")
                    addLog("호출 실패: ${callResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in callElevatorAndCheckStatus", e)
                addLog("오류: ${e.message}")
            }
        }.start()
    }

    private fun checkStatusOnly() {
        Thread {
            try {
                addLog("상태 확인 중...")
                val statusResult = ElevatorAPIClient.getElevatorStatus()

                if (statusResult.isSuccess) {
                    val statusXml = statusResult.getOrNull() ?: ""
                    val floor = XMLParser.extractFloor(statusXml)
                    if (floor != null) {
                        addLog("현재 층: $floor")
                        broadcastFloorUpdate(floor)
                    } else {
                        addLog("층 정보 파싱 실패")
                    }
                } else {
                    addLog("상태 확인 실패: ${statusResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                addLog("오류: ${e.message}")
            }
        }.start()
    }

    private fun broadcastFloorUpdate(floor: String) {
        val intent = Intent("com.elevator.webhook.FLOOR_UPDATE")
        intent.putExtra("floor", floor)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("엘레베이터 호출 서비스")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Elevator Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "엘레베이터 호출 서비스 백그라운드 실행"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message"

        val prefs = getSharedPreferences("elevator_logs", MODE_PRIVATE)
        val existingLogs = prefs.getString("log_text", "") ?: ""

        val newLogs = if (existingLogs.isEmpty()) {
            logMessage
        } else {
            "$logMessage\n$existingLogs"
        }

        // Keep last ~50 lines
        val lines = newLogs.split("\n")
        val trimmed = if (lines.size > 50) lines.take(50).joinToString("\n") else newLogs

        prefs.edit().putString("log_text", trimmed).apply()

        // Broadcast log update
        val intent = Intent("com.elevator.webhook.LOG_UPDATE")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (powerReceiver != null) {
            unregisterReceiver(powerReceiver)
            powerReceiver = null
        }
        ttsManager?.shutdown()
        Log.d(TAG, "Service destroyed")
    }
}
