package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AssistantOutcome {
    data class SpokenResponse(val replyText: String) : AssistantOutcome()
    data class DeviceAction(val deviceName: String, val action: String) : AssistantOutcome()
    data class SensorQuery(val room: String) : AssistantOutcome()
    data class CallAction(val contactName: String) : AssistantOutcome()
    data class MessageAction(val recipient: String, val messageBody: String) : AssistantOutcome()
    data class CameraAction(val mode: String = "OPEN") : AssistantOutcome()
    data class MusicAction(val command: String) : AssistantOutcome()
    data class AlarmAction(val command: String = "SHOW") : AssistantOutcome()
    data class AppAction(val appName: String) : AssistantOutcome()
    data class RoutineAction(val routineName: String) : AssistantOutcome()
    data class TimeDateAction(val format: String = "NOW") : AssistantOutcome()
    data class Error(val message: String) : AssistantOutcome()
}

class GeminiAssistant {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getSystemPrompt(isIotModeEnabled: Boolean): String {
        return if (isIotModeEnabled) {
            """
            You are NEXORA AI, a smart personal assistant for Android smartphones like Siri, with integrated ESP32 smart home automation.
            You fluently understand Hinglish, Hindi, Urdu, and English commands.
            
            Examples of commands you handle:
            - "kamre ki light on karo" / "light chala do" -> call tool control_device with action="ON", device_name="light"
            - "pankha band karo" / "fan off" -> call tool control_device with action="OFF", device_name="fan"
            - "saari lights band karo" / "all devices off" -> call tool execute_routine with routine_name="All Off"
            - "bedroom ka temperature kitna hai?" -> call tool query_sensor with room="bedroom"
            - "Ammi ko call lagao" -> call tool make_call with contact_name="Ammi"
            - "Ali ko message bhejo me 5 minute me aa raha hu" -> call tool send_message with recipient="Ali", message_body="me 5 minute me aa raha hu"
            - "camera kholo" / "take a photo" -> call tool open_camera
            - "gaana bajao" / "music pause karo" -> call tool control_music with command="PLAY" or "PAUSE"
            - "alarm dikhao" / "show alarms" -> call tool show_alarms
            - "open youtube" / "whatsapp kholo" -> call tool open_app with app_name="YouTube"
            - "good morning" -> call tool execute_routine with routine_name="Good Morning"
            - "kya time hua hai?" -> call tool get_time_date
            
            For general questions or conversational chat, provide a concise, natural, polite Hinglish or English answer (1-2 sentences maximum).
            """.trimIndent()
        } else {
            """
            You are NEXORA AI, a smart personal assistant for Android smartphones like Siri.
            You fluently understand Hinglish, Hindi, Urdu, and English commands.
            
            IMPORTANT: IoT & Smart Home mode is currently DISABLED.
            If the user asks to control smart home devices, lights, fans, appliances, sensors, ESP32, or home automation routines:
            Explain politely in natural Hinglish or English that IoT & Smart Home mode is currently turned off, and direct them to Settings under 'IoT & Smart Home' to enable it. Do NOT attempt to control any devices.
            
            Phone & Personal Assistant features you handle:
            - "Ammi ko call lagao" -> call tool make_call with contact_name="Ammi"
            - "Ali ko message bhejo me 5 minute me aa raha hu" -> call tool send_message with recipient="Ali", message_body="me 5 minute me aa raha hu"
            - "camera kholo" / "take a photo" -> call tool open_camera
            - "gaana bajao" / "music pause karo" -> call tool control_music with command="PLAY" or "PAUSE"
            - "alarm dikhao" / "show alarms" -> call tool show_alarms
            - "open youtube" / "whatsapp kholo" -> call tool open_app with app_name="YouTube"
            - "kya time hua hai?" -> call tool get_time_date
            
            For general questions, knowledge, conversation, or chat, provide a concise, natural, polite Hinglish or English answer (1-2 sentences maximum).
            """.trimIndent()
        }
    }

    suspend fun processCommand(
        prompt: String,
        knownDevices: List<String> = emptyList(),
        isIotModeEnabled: Boolean = false
    ): AssistantOutcome = withContext(Dispatchers.IO) {
        val apiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String)?.takeIf { it.isNotBlank() && !it.contains("MY_GEMINI_API_KEY") } ?: ""
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank()) {
            return@withContext parseOfflineCommand(prompt, isIotModeEnabled)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val deviceContext = if (isIotModeEnabled) "\nAvailable IoT Devices: ${knownDevices.joinToString()}" else ""
            val jsonBody = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", getSystemPrompt(isIotModeEnabled) + deviceContext)
                        })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                // Structured Tool declarations
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            // Phone Call
                            put(JSONObject().apply {
                                put("name", "make_call")
                                put("description", "Dials a contact.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("contact_name", JSONObject().apply { put("type", "STRING") })
                                    })
                                    put("required", JSONArray().apply { put("contact_name") })
                                })
                            })
                            // SMS
                            put(JSONObject().apply {
                                put("name", "send_message")
                                put("description", "Composes an SMS message.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("recipient", JSONObject().apply { put("type", "STRING") })
                                        put("message_body", JSONObject().apply { put("type", "STRING") })
                                    })
                                    put("required", JSONArray().apply { put("recipient"); put("message_body") })
                                })
                            })
                            // Camera
                            put(JSONObject().apply {
                                put("name", "open_camera")
                                put("description", "Launches phone camera.")
                                put("parameters", JSONObject().apply { put("type", "OBJECT") })
                            })
                            // Music
                            put(JSONObject().apply {
                                put("name", "control_music")
                                put("description", "Plays or pauses music.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("command", JSONObject().apply { put("type", "STRING") })
                                    })
                                })
                            })
                            // Show Alarms
                            put(JSONObject().apply {
                                put("name", "show_alarms")
                                put("description", "Opens alarm clock.")
                                put("parameters", JSONObject().apply { put("type", "OBJECT") })
                            })
                            // Open App
                            put(JSONObject().apply {
                                put("name", "open_app")
                                put("description", "Opens an application on the phone like YouTube, WhatsApp, or Chrome.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("app_name", JSONObject().apply { put("type", "STRING") })
                                    })
                                    put("required", JSONArray().apply { put("app_name") })
                                })
                            })
                            // Time
                            put(JSONObject().apply {
                                put("name", "get_time_date")
                                put("description", "Gets current time or date.")
                                put("parameters", JSONObject().apply { put("type", "OBJECT") })
                            })

                            // IoT tools are only declared when IoT Mode is explicitly enabled
                            if (isIotModeEnabled) {
                                // Device Control
                                put(JSONObject().apply {
                                    put("name", "control_device")
                                    put("description", "Controls an ESP32 light, fan, relay, or smart plug.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("device_name", JSONObject().apply { put("type", "STRING") })
                                            put("action", JSONObject().apply { put("type", "STRING"); put("description", "ON, OFF, or TOGGLE") })
                                        })
                                        put("required", JSONArray().apply { put("device_name"); put("action") })
                                    })
                                })
                                // Query Sensor
                                put(JSONObject().apply {
                                    put("name", "query_sensor")
                                    put("description", "Reads temperature, humidity, or air quality from ESP32 sensors.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("room", JSONObject().apply { put("type", "STRING") })
                                        })
                                    })
                                })
                                // Routine
                                put(JSONObject().apply {
                                    put("name", "execute_routine")
                                    put("description", "Executes an automation routine.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("routine_name", JSONObject().apply { put("type", "STRING") })
                                        })
                                        put("required", JSONArray().apply { put("routine_name") })
                                    })
                                })
                            }
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseOfflineCommand(prompt, isIotModeEnabled)
            }

            val rootJson = JSONObject(respBody)
            val candidates = rootJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("functionCall")) {
                        val fn = part.getJSONObject("functionCall")
                        val fnName = fn.optString("name")
                        val args = fn.optJSONObject("args") ?: JSONObject()

                        return@withContext when (fnName) {
                            "control_device" -> if (isIotModeEnabled) {
                                AssistantOutcome.DeviceAction(
                                    deviceName = args.optString("device_name", "light"),
                                    action = args.optString("action", "ON").uppercase()
                                )
                            } else {
                                AssistantOutcome.SpokenResponse("IoT mode abhi band hai. Devices control karne ke liye Settings me 'Enable IoT Mode' on karein.")
                            }
                            "query_sensor" -> if (isIotModeEnabled) {
                                AssistantOutcome.SensorQuery(
                                    room = args.optString("room", "bedroom")
                                )
                            } else {
                                AssistantOutcome.SpokenResponse("IoT mode abhi band hai. Sensor readings ke liye Settings me 'Enable IoT Mode' on karein.")
                            }
                            "make_call" -> AssistantOutcome.CallAction(
                                contactName = args.optString("contact_name", "Unknown")
                            )
                            "send_message" -> AssistantOutcome.MessageAction(
                                recipient = args.optString("recipient", "Unknown"),
                                messageBody = args.optString("message_body", "")
                            )
                            "open_camera" -> AssistantOutcome.CameraAction()
                            "control_music" -> AssistantOutcome.MusicAction(
                                command = args.optString("command", "TOGGLE").uppercase()
                            )
                            "show_alarms" -> AssistantOutcome.AlarmAction()
                            "open_app" -> AssistantOutcome.AppAction(
                                appName = args.optString("app_name", "App")
                            )
                            "execute_routine" -> if (isIotModeEnabled) {
                                AssistantOutcome.RoutineAction(
                                    routineName = args.optString("routine_name", "All Off")
                                )
                            } else {
                                AssistantOutcome.SpokenResponse("IoT mode abhi band hai. Routines execute karne ke liye Settings me 'Enable IoT Mode' on karein.")
                            }
                            "get_time_date" -> AssistantOutcome.TimeDateAction()
                            else -> parseOfflineCommand(prompt, isIotModeEnabled)
                        }
                    } else if (part.has("text")) {
                        val reply = part.optString("text", "")
                        if (reply.isNotBlank()) {
                            return@withContext AssistantOutcome.SpokenResponse(reply)
                        }
                    }
                }
            }

            parseOfflineCommand(prompt, isIotModeEnabled)
        } catch (e: Exception) {
            parseOfflineCommand(prompt, isIotModeEnabled)
        }
    }

    // High performance offline Hinglish/Hindi/English NLP parser
    fun parseOfflineCommand(prompt: String, isIotModeEnabled: Boolean = false): AssistantOutcome {
        val lower = prompt.lowercase().trim()

        // If IoT mode is OFF, check for IoT commands and reject them politely
        val isIotCommand = lower.contains("light") || lower.contains("batti") ||
                lower.contains("pankha") || lower.contains("fan") ||
                lower.contains("temperature") || lower.contains("temp") ||
                lower.contains("humidity") || lower.contains("sensor") ||
                lower.contains("saari lights") || lower.contains("all off") ||
                lower.contains("study mode") || lower.contains("esp32")

        if (!isIotModeEnabled && isIotCommand) {
            return AssistantOutcome.SpokenResponse(
                "IoT & Smart Home mode abhi disabled hai. Lights, fans aur ESP32 control karne ke liye Settings me jaakar 'Enable IoT Mode' on karein."
            )
        }

        return when {
            // Routines (only when IoT is enabled)
            isIotModeEnabled && (lower.contains("good morning") || lower.contains("subah bakhair") || lower.contains("suprabhat")) ->
                AssistantOutcome.RoutineAction("Good Morning")

            isIotModeEnabled && (lower.contains("all off") || lower.contains("saari light") || lower.contains("sab band") || lower.contains("all devices off")) ->
                AssistantOutcome.RoutineAction("All Off")

            isIotModeEnabled && (lower.contains("study mode") || lower.contains("padhai mode")) ->
                AssistantOutcome.RoutineAction("Study Mode")

            // IoT Light (only when IoT is enabled)
            isIotModeEnabled && (lower.contains("light on") || lower.contains("light chalao") || lower.contains("light jalao") || lower.contains("batti on") || lower.contains("turn on the light")) ->
                AssistantOutcome.DeviceAction(deviceName = extractDeviceName(lower, "light"), action = "ON")

            isIotModeEnabled && (lower.contains("light off") || lower.contains("light band") || lower.contains("light bujhao") || lower.contains("batti band") || lower.contains("turn off the light")) ->
                AssistantOutcome.DeviceAction(deviceName = extractDeviceName(lower, "light"), action = "OFF")

            // IoT Fan (only when IoT is enabled)
            isIotModeEnabled && (lower.contains("fan on") || lower.contains("pankha on") || lower.contains("pankha chalao") || lower.contains("turn on the fan")) ->
                AssistantOutcome.DeviceAction(deviceName = "fan", action = "ON")

            isIotModeEnabled && (lower.contains("fan off") || lower.contains("pankha off") || lower.contains("pankha band") || lower.contains("turn off the fan")) ->
                AssistantOutcome.DeviceAction(deviceName = "fan", action = "OFF")

            // Sensor Readings (only when IoT is enabled)
            isIotModeEnabled && (lower.contains("temperature") || lower.contains("temp") || lower.contains("tapman") || lower.contains("humidity") || lower.contains("sensor status")) ->
                AssistantOutcome.SensorQuery(room = if (lower.contains("kitchen")) "kitchen" else "bedroom")

            // Phone Calls
            lower.startsWith("call ") || lower.contains("ko phone") || lower.contains("ko call") -> {
                val target = lower
                    .replace("hey seeru", "")
                    .replace("hey nexora", "")
                    .replace("nexora", "")
                    .replace("seeru", "")
                    .replace("ko phone lagao", "")
                    .replace("ko call lagao", "")
                    .replace("ko call karo", "")
                    .replace("ko phone karo", "")
                    .replace("ko call", "")
                    .replace("ko phone", "")
                    .replace("call lagao", "")
                    .replace("call karo", "")
                    .replace("call ", "")
                    .trim()
                AssistantOutcome.CallAction(contactName = target.ifEmpty { "Ammi" })
            }

            // Messages / SMS
            lower.startsWith("send a message") || lower.startsWith("send message") || lower.contains("message bhejo") || lower.contains("msg bhejo") -> {
                AssistantOutcome.MessageAction(recipient = "Ali", messageBody = "Hello from Nexora AI!")
            }

            // Camera
            lower.contains("camera") || lower.contains("photo khicho") || lower.contains("tasveer") ->
                AssistantOutcome.CameraAction()

            // Music
            lower.contains("music") || lower.contains("gaana") || lower.contains("song") -> {
                val cmd = if (lower.contains("pause") || lower.contains("band") || lower.contains("stop")) "PAUSE" else "PLAY"
                AssistantOutcome.MusicAction(cmd)
            }

            // Alarms
            lower.contains("alarm") || lower.contains("alarms") ->
                AssistantOutcome.AlarmAction()

            // Open Apps
            lower.startsWith("open ") || lower.contains("kholo") -> {
                val app = lower.replace("open ", "").replace("kholo", "").trim()
                AssistantOutcome.AppAction(appName = app.ifEmpty { "YouTube" })
            }

            // Time & Date
            lower.contains("time") || lower.contains("samay") || lower.contains("waqt") || lower.contains("date") ->
                AssistantOutcome.TimeDateAction()

            // Greetings & Chat
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") || lower.contains("salam") ->
                AssistantOutcome.SpokenResponse("Namaste! Mai Nexora hu. Mai aapki kya madad kar sakti hu?")

            lower.contains("kaise ho") || lower.contains("how are you") ->
                AssistantOutcome.SpokenResponse("Mai bilkul theek hu! Aap batayein, mai aapki kya madad karu?")

            else ->
                AssistantOutcome.SpokenResponse(
                    if (isIotModeEnabled) {
                        "Aap mujhe phone call, message, camera, music, light on/off ya temperature ke commands de sakte hain."
                    } else {
                        "Mai aapka personal voice assistant hu. Aap mujhe call lagane, message bhejne, camera kholne, music chalane ya sawal puchne ke commands de sakte hain."
                    }
                )
        }
    }

    private fun extractDeviceName(text: String, defaultName: String): String {
        return when {
            text.contains("bedroom") -> "bedroom $defaultName"
            text.contains("kitchen") -> "kitchen $defaultName"
            text.contains("study") -> "desk lamp"
            else -> defaultName
        }
    }
}
