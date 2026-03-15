package com.elevator.webhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
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

    private var chargerReceiver: BroadcastReceiver? = null
    private var floorReceiver: BroadcastReceiver? = null
    private var logReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        startElevatorService()
        registerUIReceivers()
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
    }

    private fun setupListeners() {
        callButton.setOnClickListener {
            val intent = Intent(this, ElevatorService::class.java)
            intent.action = ElevatorService.ACTION_CALL_ELEVATOR
            startForegroundService(intent)
        }

        statusButton.setOnClickListener {
            val intent = Intent(this, ElevatorService::class.java)
            intent.action = ElevatorService.ACTION_CHECK_STATUS
            startForegroundService(intent)
        }
    }

    private fun startElevatorService() {
        val intent = Intent(this, ElevatorService::class.java)
        startForegroundService(intent)
    }

    private fun registerUIReceivers() {
        // Charger state updates from service
        chargerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val isCharging = intent?.getBooleanExtra("is_charging", false) ?: false
                runOnUiThread {
                    chargerStatusTV.text = if (isCharging) {
                        getString(R.string.charger_connected)
                    } else {
                        getString(R.string.charger_disconnected)
                    }
                }
            }
        }
        registerReceiver(
            chargerReceiver,
            IntentFilter("com.elevator.webhook.CHARGER_STATE_CHANGED"),
            Context.RECEIVER_NOT_EXPORTED
        )

        // Floor updates from service
        floorReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val floor = intent?.getStringExtra("floor") ?: return
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                runOnUiThread {
                    floorInfoTV.text = getString(R.string.last_floor, floor)
                    updateTimeTV.text = getString(R.string.last_update, timestamp)
                }
            }
        }
        registerReceiver(
            floorReceiver,
            IntentFilter("com.elevator.webhook.FLOOR_UPDATE"),
            Context.RECEIVER_NOT_EXPORTED
        )

        // Log updates from service
        logReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                loadLogs()
            }
        }
        registerReceiver(
            logReceiver,
            IntentFilter("com.elevator.webhook.LOG_UPDATE"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun updateChargerStatus() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter) ?: return
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val isCharging = plugged > 0

        chargerStatusTV.text = if (isCharging) {
            getString(R.string.charger_connected)
        } else {
            getString(R.string.charger_disconnected)
        }
    }

    private fun loadLogs() {
        val prefs = getSharedPreferences("elevator_logs", MODE_PRIVATE)
        val logs = prefs.getString("log_text", "") ?: ""

        runOnUiThread {
            logTV.text = logs
        }
    }

    override fun onResume() {
        super.onResume()
        updateChargerStatus()
        loadLogs()
    }

    override fun onDestroy() {
        super.onDestroy()
        chargerReceiver?.let { unregisterReceiver(it) }
        floorReceiver?.let { unregisterReceiver(it) }
        logReceiver?.let { unregisterReceiver(it) }
    }
}
