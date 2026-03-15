package com.elevator.webhook

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var chargerStatusTV: TextView
    private lateinit var floorInfoTV: TextView
    private lateinit var updateTimeTV: TextView
    private lateinit var logTV: TextView
    private lateinit var callButton: Button
    private lateinit var statusButton: Button
    private lateinit var scrollView: ScrollView

    private var batteryReceiver: BatteryChangeReceiver? = null
    private var currentFloor: String? = null
    private var currentChargerStatus: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        registerBatteryReceiver()
        updateChargerStatus()
        loadLogs()
    }

    private fun initViews() {
        chargerStatusTV = findViewById(R.id.charger_status)
        floorInfoTV = findViewById(R.id.floor_info)
        updateTimeTV = findViewById(R.id.update_time)
        logTV = findViewById(R.id.log_view)
        callButton = findViewById(R.id.call_button)
        statusButton = findViewById(R.id.status_button)
        scrollView = findViewById(R.id.log_scroll_view)

        logTV.movementMethod = ScrollingMovementMethod()
    }

    private fun setupListeners() {
        callButton.setOnClickListener {
            callElevator()
        }

        statusButton.setOnClickListener {
            checkStatus()
        }
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = BatteryChangeReceiver()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun updateChargerStatus() {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, ifilter) ?: return

        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        currentChargerStatus = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL) && plugged > 0

        chargerStatusTV.text = if (currentChargerStatus) {
            getString(R.string.charger_connected)
        } else {
            getString(R.string.charger_disconnected)
        }
    }

    private fun callElevator() {
        Thread {
            try {
                addLog("엘레베이터 호출 중...")
                val callResult = ElevatorAPIClient.callElevator()

                if (callResult.isSuccess) {
                    addLog("호출 성공")

                    Thread.sleep(2000)

                    checkStatus()
                } else {
                    addLog("호출 실패: ${callResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error calling elevator", e)
                addLog("오류: ${e.message}")
            }
        }.start()
    }

    private fun checkStatus() {
        Thread {
            try {
                addLog("상태 확인 중...")
                val statusResult = ElevatorAPIClient.getElevatorStatus()

                if (statusResult.isSuccess) {
                    val statusXml = statusResult.getOrNull() ?: ""
                    val floor = XMLParser.extractFloor(statusXml)

                    if (floor != null) {
                        currentFloor = floor
                        addLog("현재 층: $floor")

                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        runOnUiThread {
                            floorInfoTV.text = getString(R.string.last_floor, floor)
                            updateTimeTV.text = getString(R.string.last_update, timestamp)
                        }
                    } else {
                        addLog("층 정보 파싱 실패")
                    }
                } else {
                    addLog("상태 확인 실패: ${statusResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking status", e)
                addLog("오류: ${e.message}")
            }
        }.start()
    }

    private fun loadLogs() {
        val prefs = getSharedPreferences("elevator_logs", MODE_PRIVATE)
        val logs = prefs.getStringSet("logs", LinkedHashSet()) ?: LinkedHashSet()

        runOnUiThread {
            logTV.text = logs.reversed().joinToString("\n")
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message"

        val prefs = getSharedPreferences("elevator_logs", MODE_PRIVATE)
        val logs = prefs.getStringSet("logs", LinkedHashSet()) ?: LinkedHashSet()

        val newLogs = logs.toMutableSet()
        newLogs.add(logMessage)

        if (newLogs.size > 50) {
            newLogs.remove(newLogs.first())
        }

        prefs.edit().putStringSet("logs", newLogs).apply()

        runOnUiThread {
            logTV.text = newLogs.reversed().joinToString("\n")
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver)
        }
    }
}
