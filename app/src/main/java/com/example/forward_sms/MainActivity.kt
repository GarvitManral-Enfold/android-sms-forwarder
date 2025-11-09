package com.example.forward_sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 1
    private val BATTERY_OPTIMIZATION_REQUEST_CODE = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val requestPermissionsButton = findViewById<Button>(R.id.requestPermissionsButton)
        val disableBatteryButton = findViewById<Button>(R.id.disableBatteryButton)
        val startServiceButton = findViewById<Button>(R.id.startServiceButton)

        updateStatus(statusText)

        requestPermissionsButton.setOnClickListener {
            requestSmsPermissions()
        }

        disableBatteryButton.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        startServiceButton.setOnClickListener {
            startForegroundService()
        }
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        updateStatus(statusText)
    }

    private fun updateStatus(statusText: TextView) {
        val permissionsGranted = checkPermissions()
        val isBatteryOptimized = isBatteryOptimizationEnabled()
        val isServiceRunning = SmsForwardingService.isRunning

        val status = buildString {
            append("SMS Permissions: ${if (permissionsGranted) "✓ Granted" else "✗ Not Granted"}\n")
            append("Battery Optimization: ${if (!isBatteryOptimized) "✓ Disabled" else "✗ Enabled (Disable for reliability)"}\n")
            append("Background Service: ${if (isServiceRunning) "✓ Running" else "✗ Stopped"}\n\n")

            if (permissionsGranted && isServiceRunning) {
                append("✓ SMS Forwarding Active!\n")
                append("Google Messages remains your default SMS app.\n")
                append("All incoming SMS will be forwarded to 5556.")
            } else if (!permissionsGranted) {
                append("⚠ Grant SMS permissions first")
            } else {
                append("⚠ Start the background service")
            }
        }
        statusText.text = status
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun isBatteryOptimizationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return false
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Disable Battery Optimization")
                    .setMessage("For reliable SMS forwarding in background, disable battery optimization.\n\nThis allows the app to run continuously even when screen is off.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "Battery optimization already disabled ✓", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Not needed on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundService() {
        if (checkPermissions()) {
            val intent = Intent(this, SmsForwardingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Background service started ✓", Toast.LENGTH_SHORT).show()

            // Update status after a short delay
            findViewById<TextView>(R.id.statusText).postDelayed({
                updateStatus(findViewById(R.id.statusText))
            }, 500)
        } else {
            Toast.makeText(this, "Grant SMS permissions first!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val statusText = findViewById<TextView>(R.id.statusText)
            updateStatus(statusText)

            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All permissions granted! ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions denied. App won't work properly.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val statusText = findViewById<TextView>(R.id.statusText)
        updateStatus(statusText)
    }
}