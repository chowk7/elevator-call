package com.elevator.webhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.content.SharedPreferences

class BatteryChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED && context != null) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL) && plugged > 0

            val prefs = context.getSharedPreferences("elevator_prefs", Context.MODE_PRIVATE)
            val wasCharging = prefs.getBoolean("was_charging", false)

            // Detect transition from charging to not charging
            if (wasCharging && !isCharging) {
                val serviceIntent = Intent(context, ElevatorService::class.java)
                context.startForegroundService(serviceIntent)
            }

            // Update the charging state
            prefs.edit().putBoolean("was_charging", isCharging).apply()
        }
    }
}
