package com.example.forward_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SMS_FORWARDER"
        // Emulator 2 (emulator-5556) wlan0 IP address
        private const val FORWARD_URL = "http://10.0.2.16:8080/sms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "========== SMS RECEIVED - onReceive CALLED ==========")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras}")

        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") {
            Log.d(TAG, "Not SMS_RECEIVED action, ignoring")
            return
        }

        val bundle: Bundle? = intent.extras
        if (bundle == null) {
            Log.e(TAG, "Bundle is null!")
            return
        }

        val pdus = bundle.get("pdus") as? Array<*>
        if (pdus == null || pdus.isEmpty()) {
            Log.e(TAG, "No PDUs found!")
            return
        }

        val format = bundle.getString("format")
        Log.d(TAG, "Processing ${pdus.size} message(s), format: $format")

        pdus.forEach { pdu ->
            try {
                val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }

                val sender = sms.originatingAddress ?: "Unknown"
                val messageBody = sms.messageBody ?: ""
                val timestamp = sms.timestampMillis

                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "From: $sender")
                Log.d(TAG, "Message: $messageBody")
                Log.d(TAG, "Time: $timestamp")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Forward SMS data via HTTP
                Log.d(TAG, "About to call forwardViaHttp...")
                forwardViaHttp(sender, messageBody, timestamp)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
                e.printStackTrace()
            }
        }
    }

    private fun forwardViaHttp(sender: String, message: String, timestamp: Long) {
        Log.d(TAG, "forwardViaHttp() called - launching coroutine")

        CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "Creating URL connection to: $FORWARD_URL")
                val url = URL(FORWARD_URL)
                connection = url.openConnection() as HttpURLConnection

                Log.d(TAG, "Configuring connection...")
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // Escape JSON strings properly
                val escapedSender = sender.replace("\"", "\\\"")
                val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")

                val jsonData = """
                    {
                        "sender": "$escapedSender",
                        "message": "$escapedMessage",
                        "timestamp": $timestamp
                    }
                """.trimIndent()

                Log.d(TAG, "JSON payload: $jsonData")
                Log.d(TAG, "Connecting and sending data...")

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonData)
                    writer.flush()
                    Log.d(TAG, "Data written and flushed")
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")

                // Read response body
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                }

                Log.d(TAG, "Response body: $response")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "✓ SMS forwarded successfully via HTTP")
                } else {
                    Log.e(TAG, "✗ HTTP forward failed with code: $responseCode")
                }

            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "✗ Unknown host - check if server is running and URL is correct", e)
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "✗ Connection refused - server not reachable at $FORWARD_URL", e)
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "✗ Connection timeout - server took too long to respond", e)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to forward via HTTP: ${e.javaClass.simpleName} - ${e.message}", e)
                e.printStackTrace()
            } finally {
                connection?.disconnect()
                Log.d(TAG, "Connection closed")
            }
        }
    }
}