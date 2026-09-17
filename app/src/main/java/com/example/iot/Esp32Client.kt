package com.example.iot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DeviceActionResult(
    val isSuccess: Boolean,
    val newState: Boolean,
    val message: String,
    val temperature: Float? = null,
    val humidity: Float? = null
)

class Esp32Client {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendDeviceCommand(
        ip: String,
        port: Int = 80,
        pin: Int,
        action: String, // "ON", "OFF", "TOGGLE"
        token: String = "seeru_secret_token"
    ): DeviceActionResult = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:$port/api/device"
            val jsonPayload = JSONObject().apply {
                put("action", action)
                put("pin", pin)
                put("token", token)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = JSONObject(body)
                    val state = parsed.optBoolean("state", action.equals("ON", ignoreCase = true))
                    val msg = parsed.optString("message", "Device updated successfully")
                    DeviceActionResult(
                        isSuccess = true,
                        newState = state,
                        message = msg
                    )
                } else {
                    DeviceActionResult(
                        isSuccess = false,
                        newState = false,
                        message = "ESP32 returned HTTP error ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            DeviceActionResult(
                isSuccess = false,
                newState = false,
                message = "Device unreachable at $ip: ${e.localizedMessage ?: "Connection timed out"}"
            )
        }
    }

    suspend fun querySensor(
        ip: String,
        port: Int = 80,
        token: String = "seeru_secret_token"
    ): DeviceActionResult = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:$port/api/sensors?token=$token"
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = JSONObject(body)
                    val temp = parsed.optDouble("temperature", 0.0).toFloat()
                    val hum = parsed.optDouble("humidity", 0.0).toFloat()
                    DeviceActionResult(
                        isSuccess = true,
                        newState = true,
                        message = "Sensor data retrieved",
                        temperature = temp,
                        humidity = hum
                    )
                } else {
                    DeviceActionResult(
                        isSuccess = false,
                        newState = false,
                        message = "Sensor HTTP error ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            DeviceActionResult(
                isSuccess = false,
                newState = false,
                message = "Sensor unreachable at $ip: ${e.localizedMessage ?: "Connection timed out"}"
            )
        }
    }
}
