package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class AssistantOutcome {
    data class SpokenResponse(val replyText: String) : AssistantOutcome()
    data class DeviceAction(val deviceName: String, val action: String) : AssistantOutcome()
    data class SensorQuery(val room: String) : AssistantOutcome()
    data class CallAction(val contactName: String) : AssistantOutcome()
    data class MessageAction(val recipient: String, val messageBody: String) : AssistantOutcome()
    data class WhatsAppAction(val contactOrPhone: String, val messageBody: String) : AssistantOutcome()
    data class CameraAction(val mode: String = "OPEN") : AssistantOutcome()
    data class MusicAction(val command: String, val songQuery: String = "") : AssistantOutcome()
    data class AlarmAction(val command: String = "SET", val hour: Int = -1, val minute: Int = -1, val label: String = "Nexora Alarm") : AssistantOutcome()
    data class WeatherAction(val city: String = "") : AssistantOutcome()
    data class YouTubeAction(val query: String = "") : AssistantOutcome()
    data class AppAction(val appName: String) : AssistantOutcome()
    data class ContactUpdateAction(val alias: String, val newNumber: String) : AssistantOutcome()
    data class RoutineAction(val routineName: String) : AssistantOutcome()
    data class TimeDateAction(val format: String = "NOW") : AssistantOutcome()
    data class Error(val message: String) : AssistantOutcome()
}

class GeminiAssistant {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getApiKey(): String {
        return try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String)?.takeIf { it.isNotBlank() && !it.contains("MY_GEMINI_API_KEY") } ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

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
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

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
                            // Set Alarm
                            put(JSONObject().apply {
                                put("name", "set_alarm")
                                put("description", "Sets a phone alarm with optional hour and minute.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("hour", JSONObject().apply { put("type", "INTEGER"); put("description", "Hour (0-23)") })
                                        put("minute", JSONObject().apply { put("type", "INTEGER"); put("description", "Minute (0-59)") })
                                        put("label", JSONObject().apply { put("type", "STRING") })
                                    })
                                })
                            })
                            // Open YouTube
                            put(JSONObject().apply {
                                put("name", "open_youtube")
                                put("description", "Opens YouTube app or search query.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("query", JSONObject().apply { put("type", "STRING") })
                                    })
                                })
                            })
                            // Send WhatsApp Message
                            put(JSONObject().apply {
                                put("name", "send_whatsapp")
                                put("description", "Sends or composes a WhatsApp message.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("contact_or_phone", JSONObject().apply { put("type", "STRING") })
                                        put("message_body", JSONObject().apply { put("type", "STRING") })
                                    })
                                })
                            })
                            // Weather
                            put(JSONObject().apply {
                                put("name", "get_weather")
                                put("description", "Shows weather forecast for a city or current location.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("city", JSONObject().apply { put("type", "STRING") })
                                    })
                                })
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
                            "show_alarms" -> AssistantOutcome.AlarmAction(command = "SHOW")
                            "set_alarm" -> AssistantOutcome.AlarmAction(
                                command = "SET",
                                hour = args.optInt("hour", -1),
                                minute = args.optInt("minute", -1),
                                label = args.optString("label", "Nexora Alarm")
                            )
                            "open_youtube" -> AssistantOutcome.YouTubeAction(
                                query = args.optString("query", "")
                            )
                            "send_whatsapp" -> AssistantOutcome.WhatsAppAction(
                                contactOrPhone = args.optString("contact_or_phone", ""),
                                messageBody = args.optString("message_body", "")
                            )
                            "get_weather" -> AssistantOutcome.WeatherAction(
                                city = args.optString("city", "")
                            )
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

        // 1. Check for stand-alone wake word (Immediate Siri-style greeting)
        if (lower == "hey" || lower == "hey nexora" || lower == "hey seeru" || lower == "hey nexora ai" || lower == "nexora" || lower == "seeru") {
            return AssistantOutcome.SpokenResponse("Ji! Mai sun rahi hu, bataiye kya madad karu?")
        }

        // 2. Strip leading wake-words so following commands execute seamlessly
        val cleanCmd = lower
            .replace(Regex("""^(?:hey\s+(?:nexora|seeru)?|nexora|seeru)\s*"""), "")
            .trim()
        val cmd = if (cleanCmd.isNotBlank()) cleanCmd else lower

        // 3. Contact Number Update via Voice (e.g. "Ammi ka number 9876543210 karo")
        val updatePattern = Regex("""([a-zA-Z0-9_\u0600-\u06FF\u0900-\u097F]+)\s+ka\s+number\s+([0-9+]{7,15})""", RegexOption.IGNORE_CASE)
        val updateMatch = updatePattern.find(cmd)
        if (updateMatch != null) {
            val alias = updateMatch.groupValues[1].trim()
            val num = updateMatch.groupValues[2].trim()
            return AssistantOutcome.ContactUpdateAction(alias = alias, newNumber = num)
        }

        // If IoT mode is OFF, check for IoT commands and reject them politely
        val isIotCommand = cmd.contains("light") || cmd.contains("batti") ||
                cmd.contains("pankha") || cmd.contains("fan") ||
                cmd.contains("temperature") || cmd.contains("temp") ||
                cmd.contains("humidity") || cmd.contains("sensor") ||
                cmd.contains("saari lights") || cmd.contains("all off") ||
                cmd.contains("study mode") || cmd.contains("esp32")

        if (!isIotModeEnabled && isIotCommand) {
            return AssistantOutcome.SpokenResponse(
                "IoT & Smart Home mode abhi disabled hai. Lights, fans aur ESP32 control karne ke liye Settings me jaakar 'Enable IoT Mode' on karein."
            )
        }

        return when {
            // YouTube (Voice trigger: "open youtube", "youtube chalao", "search X on youtube")
            cmd.contains("youtube") -> {
                val query = extractYouTubeQuery(cmd)
                AssistantOutcome.YouTubeAction(query = query)
            }

            // WhatsApp (Voice trigger: "send whatsapp message", "whatsapp karo", "whatsapp pe message bhejo")
            cmd.contains("whatsapp") -> {
                val (rec, body) = extractWhatsAppDetails(cmd)
                AssistantOutcome.WhatsAppAction(contactOrPhone = rec, messageBody = body)
            }

            // Alarms (Voice trigger: "set alarm", "alarm lagao", "7 baje ka alarm lagao", "alarms dikhao")
            cmd.contains("alarm") || cmd.contains("alarms") || cmd.contains("baje utha dena") || cmd.contains("baje jaga dena") -> {
                val (h, m) = extractAlarmTime(cmd)
                AssistantOutcome.AlarmAction(command = "SET", hour = h, minute = m)
            }

            // Weather (Voice trigger: "show weather", "mausam kaisa hai", "weather kya hai", "aaj ka mausam")
            cmd.contains("weather") || cmd.contains("mausam") || cmd.contains("tapman") || cmd.contains("hawa") -> {
                val city = extractWeatherCity(cmd)
                AssistantOutcome.WeatherAction(city = city)
            }

            // Phone Calls
            cmd.startsWith("call ") || cmd.contains("ko phone") || cmd.contains("ko call") ||
                    cmd.contains("call lagao") || cmd.contains("call karo") || cmd.contains("phone lagao") -> {
                val target = cmd
                    .replace("ko phone lagao", "")
                    .replace("ko call lagao", "")
                    .replace("ko call karo", "")
                    .replace("ko phone karo", "")
                    .replace("ko call", "")
                    .replace("ko phone", "")
                    .replace("call lagao", "")
                    .replace("call karo", "")
                    .replace("phone lagao", "")
                    .replace("call ", "")
                    .trim()
                AssistantOutcome.CallAction(contactName = target.ifEmpty { "Ammi" })
            }

            // Messages / SMS
            cmd.contains("message") || cmd.contains("msg") || cmd.contains("sms") || cmd.contains("text karo") || cmd.startsWith("send message") -> {
                val (recipient, body) = extractMessageDetails(cmd)
                AssistantOutcome.MessageAction(recipient = recipient, messageBody = body)
            }

            // Camera
            cmd.contains("camera") || cmd.contains("photo khicho") || cmd.contains("tasveer") ||
                    cmd.contains("photo lo") || cmd.contains("selfie") || cmd.contains("picture") ->
                AssistantOutcome.CameraAction()

            // Music / Songs
            cmd.contains("music") || cmd.contains("gaana") || cmd.contains("gana") || cmd.contains("song") || cmd.contains("geet") -> {
                val isPause = cmd.contains("pause") || cmd.contains("band") || cmd.contains("stop") || cmd.contains("roko")
                val command = if (isPause) "PAUSE" else "PLAY"
                val songQuery = if (isPause) "" else extractSongQuery(cmd)
                AssistantOutcome.MusicAction(command = command, songQuery = songQuery)
            }

            // Open Apps (e.g. "open calculator", "calculator open karo", "instagram kholo", "settings kholo")
            cmd.startsWith("open ") || cmd.contains("kholo") || cmd.contains("chalao") ||
                    cmd.contains("launch ") || cmd.contains("start ") || cmd.contains("open karo") ||
                    cmd.contains("calculator") || cmd.contains("settings") || cmd.contains("clock") ||
                    cmd.contains("chrome") || cmd.contains("gallery") || cmd.contains("photos") ||
                    cmd.contains("files") || cmd.contains("instagram") || cmd.contains("spotify") ||
                    cmd.contains("facebook") || cmd.contains("snapchat") || cmd.contains("telegram") ||
                    cmd.contains("map") || cmd.contains("dialer") -> {
                val app = cmd
                    .replace("open karo", "")
                    .replace("kholo", "")
                    .replace("chalao", "")
                    .replace("start karo", "")
                    .replace("launch", "")
                    .replace("start", "")
                    .replace("open", "")
                    .replace("app", "")
                    .replace("dikhao", "")
                    .trim()
                AssistantOutcome.AppAction(appName = app.ifEmpty { cmd })
            }

            // Routines (only when IoT is enabled)
            isIotModeEnabled && (cmd.contains("good morning") || cmd.contains("subah bakhair") || cmd.contains("suprabhat")) ->
                AssistantOutcome.RoutineAction("Good Morning")

            isIotModeEnabled && (cmd.contains("all off") || cmd.contains("saari light") || cmd.contains("sab band") || cmd.contains("all devices off")) ->
                AssistantOutcome.RoutineAction("All Off")

            isIotModeEnabled && (cmd.contains("study mode") || cmd.contains("padhai mode")) ->
                AssistantOutcome.RoutineAction("Study Mode")

            // IoT Light (only when IoT is enabled)
            isIotModeEnabled && (cmd.contains("light on") || cmd.contains("light chalao") || cmd.contains("light jalao") || cmd.contains("batti on") || cmd.contains("turn on the light")) ->
                AssistantOutcome.DeviceAction(deviceName = extractDeviceName(cmd, "light"), action = "ON")

            isIotModeEnabled && (cmd.contains("light off") || cmd.contains("light band") || cmd.contains("light bujhao") || cmd.contains("batti band") || cmd.contains("turn off the light")) ->
                AssistantOutcome.DeviceAction(deviceName = extractDeviceName(cmd, "light"), action = "OFF")

            // IoT Fan (only when IoT is enabled)
            isIotModeEnabled && (cmd.contains("fan on") || cmd.contains("pankha on") || cmd.contains("pankha chalao") || cmd.contains("turn on the fan")) ->
                AssistantOutcome.DeviceAction(deviceName = "fan", action = "ON")

            isIotModeEnabled && (cmd.contains("fan off") || cmd.contains("pankha off") || cmd.contains("pankha band") || cmd.contains("turn off the fan")) ->
                AssistantOutcome.DeviceAction(deviceName = "fan", action = "OFF")

            // Sensor Readings (only when IoT is enabled)
            isIotModeEnabled && (cmd.contains("temperature") || cmd.contains("temp") || cmd.contains("humidity") || cmd.contains("sensor status")) ->
                AssistantOutcome.SensorQuery(room = if (cmd.contains("kitchen")) "kitchen" else "bedroom")

            // Time & Date
            cmd.contains("time") || cmd.contains("samay") || cmd.contains("waqt") || cmd.contains("date") ->
                AssistantOutcome.TimeDateAction()

            // Identity & Greetings
            cmd.contains("kaun ho") || cmd.contains("who are you") || cmd.contains("naam kya") ->
                AssistantOutcome.SpokenResponse("Mai Nexora hu, aapka smart personal voice assistant. Mai YouTube open kar sakti hu, WhatsApp message bhej sakti hu, alarms set kar sakti hu, weather bata sakti hu aur calls mila sakti hu.")

            cmd.contains("hello") || cmd.contains("hi") || cmd.contains("namaste") || cmd.contains("salam") ->
                AssistantOutcome.SpokenResponse("Namaste! Mai Nexora hu. Mai aapki kya madad kar sakti hu?")

            cmd.contains("kaise ho") || cmd.contains("how are you") ->
                AssistantOutcome.SpokenResponse("Mai bilkul theek hu! Aap batayein, mai aapki kya madad karu?")

            else ->
                AssistantOutcome.SpokenResponse(
                    if (isIotModeEnabled) {
                        "Aap mujhe phone call, WhatsApp, YouTube, alarm, weather ya light on/off ke commands de sakte hain."
                    } else {
                        "Mai aapka voice assistant hu. Aap mujhe 'Open YouTube', 'Send WhatsApp message', 'Set alarm', 'Show weather', 'Open Calculator' ya call lagane ke commands de sakte hain."
                    }
                )
        }
    }

    private fun extractYouTubeQuery(text: String): String {
        return text
            .replace("hey seeru", "")
            .replace("hey nexora", "")
            .replace("nexora", "")
            .replace("seeru", "")
            .replace("hey", "")
            .replace("open youtube and search", "")
            .replace("open youtube", "")
            .replace("youtube open karo", "")
            .replace("youtube chalao", "")
            .replace("youtube par search karo", "")
            .replace("youtube pe search karo", "")
            .replace("youtube pe chalao", "")
            .replace("youtube par chalao", "")
            .replace("search on youtube", "")
            .replace("search youtube for", "")
            .replace("youtube search", "")
            .replace("youtube", "")
            .trim()
    }

    private fun extractWhatsAppDetails(text: String): Pair<String, String> {
        val clean = text
            .replace("hey seeru", "")
            .replace("hey nexora", "")
            .replace("nexora", "")
            .replace("seeru", "")
            .replace("hey", "")
            .trim()

        val koPattern = Regex("""^([a-zA-Z0-9_\u0600-\u06FF\u0900-\u097F]+)\s+ko\s+whatsapp(?:\s+message|\s+msg)?\s*(?:bhejo|karo|send\s*karo)?\s*(.*)$""", RegexOption.IGNORE_CASE)
        val koMatch = koPattern.find(clean)
        if (koMatch != null) {
            val rec = koMatch.groupValues[1].trim()
            val body = koMatch.groupValues[2].trim()
            return Pair(rec, body.ifEmpty { "Hello!" })
        }

        val revKoWaPattern = Regex("""^(?:whatsapp|send\s+whatsapp)\s+(?:message\s+)?(?:bhejo\s+)?([a-zA-Z0-9_\u0600-\u06FF\u0900-\u097F]+)\s+ko\s*(.*)$""", RegexOption.IGNORE_CASE)
        val revKoWaMatch = revKoWaPattern.find(clean)
        if (revKoWaMatch != null) {
            val rec = revKoWaMatch.groupValues[1].trim()
            val body = revKoWaMatch.groupValues[2].trim()
            return Pair(rec, body.ifEmpty { "Hello!" })
        }

        val enPattern = Regex("""^(?:send\s+)?whatsapp(?:\s+message)?\s+to\s+([a-zA-Z0-9_]+)(?:\s+saying|\s+that)?\s*(.*)$""", RegexOption.IGNORE_CASE)
        val enMatch = enPattern.find(clean)
        if (enMatch != null) {
            val rec = enMatch.groupValues[1].trim()
            val body = enMatch.groupValues[2].trim()
            return Pair(rec, body.ifEmpty { "Hello!" })
        }

        val genericBody = clean
            .replace("send whatsapp message", "")
            .replace("whatsapp message bhejo", "")
            .replace("whatsapp message karo", "")
            .replace("whatsapp message", "")
            .replace("whatsapp karo", "")
            .replace("whatsapp", "")
            .trim()

        return Pair("", genericBody.ifEmpty { "Hello from Nexora AI!" })
    }

    private fun extractAlarmTime(text: String): Pair<Int, Int> {
        val clean = text.lowercase()
        val isPm = clean.contains("pm") || clean.contains("shaam") || clean.contains("raat")

        val colonMatch = Regex("""(\d{1,2}):(\d{2})""").find(clean)
        if (colonMatch != null) {
            var h = colonMatch.groupValues[1].toIntOrNull() ?: -1
            val m = colonMatch.groupValues[2].toIntOrNull() ?: 0
            if (isPm && h in 1..11) h += 12
            return Pair(h, m)
        }

        val numMatch = Regex("""(\d{1,2})\s*(?:am|pm|baje|\s*o'clock)?""").find(clean)
        if (numMatch != null) {
            var h = numMatch.groupValues[1].toIntOrNull() ?: -1
            if (h in 1..24) {
                if (isPm && h in 1..11) h += 12
                return Pair(h, 0)
            }
        }

        return Pair(-1, -1)
    }

    private fun extractWeatherCity(text: String): String {
        val clean = text
            .replace("hey seeru", "")
            .replace("hey nexora", "")
            .replace("nexora", "")
            .replace("seeru", "")
            .replace("hey", "")
            .replace("show weather in", "")
            .replace("show weather for", "")
            .replace("show weather", "")
            .replace("weather of", "")
            .replace("weather in", "")
            .replace("weather", "")
            .replace("ka mausam kaisa hai", "")
            .replace("ka mausam", "")
            .replace("mausam kaisa hai", "")
            .replace("mausam", "")
            .replace("tapman", "")
            .trim()
        return if (clean.length in 2..30) clean else "Delhi"
    }

    private fun extractMessageDetails(text: String): Pair<String, String> {
        val clean = text
            .replace("hey seeru", "")
            .replace("hey nexora", "")
            .replace("seeru", "")
            .replace("nexora", "")
            .trim()

        // Match pattern: "[Name] ko message/sms bhejo/karo [Body]"
        val koPattern = Regex("""^([a-zA-Z0-9_\u0600-\u06FF\u0900-\u097F]+)\s+ko\s+(?:message|msg|sms)\s+(?:bhejo|karo|send\s*karo)\s*(.*)$""")
        val koMatch = koPattern.find(clean)
        if (koMatch != null) {
            val rec = koMatch.groupValues[1].trim()
            val body = koMatch.groupValues[2].trim()
            return Pair(rec, if (body.isNotBlank()) body else "Hello!")
        }

        val revKoPattern = Regex("""^(?:message|msg|sms|send\s+message)\s+(?:bhejo\s+)?([a-zA-Z0-9_\u0600-\u06FF\u0900-\u097F]+)\s+ko\s*(.*)$""")
        val revKoMatch = revKoPattern.find(clean)
        if (revKoMatch != null) {
            val rec = revKoMatch.groupValues[1].trim()
            val body = revKoMatch.groupValues[2].trim()
            return Pair(rec, if (body.isNotBlank()) body else "Hello!")
        }

        // Match English: "send message to [Name] saying [Body]"
        val enPattern = Regex("""^send\s+(?:a\s+)?(?:message|sms)\s+to\s+([a-zA-Z0-9_]+)(?:\s+saying|\s+that)?\s*(.*)$""")
        val enMatch = enPattern.find(clean)
        if (enMatch != null) {
            val rec = enMatch.groupValues[1].trim()
            val body = enMatch.groupValues[2].trim()
            return Pair(rec, if (body.isNotBlank()) body else "Hello!")
        }

        // Generic: "message bhejo [body]" or "message send karo [body]"
        val genericBody = clean
            .replace("message send karo", "")
            .replace("message bhejo", "")
            .replace("message karo", "")
            .replace("msg bhejo", "")
            .replace("sms bhejo", "")
            .replace("sms karo", "")
            .replace("send message", "")
            .trim()

        return Pair("Ali", if (genericBody.isNotBlank()) genericBody else "Hello from Seeru AI!")
    }

    private fun extractSongQuery(text: String): String {
        return text
            .replace("hey seeru", "")
            .replace("hey nexora", "")
            .replace("seeru", "")
            .replace("nexora", "")
            .replace("gaana bajao", "")
            .replace("gana bajao", "")
            .replace("song play karo", "")
            .replace("play song", "")
            .replace("play music", "")
            .replace("play ", "")
            .replace("music chalao", "")
            .replace("song chalao", "")
            .replace("song", "")
            .trim()
    }

    private fun extractDeviceName(text: String, defaultName: String): String {
        return when {
            text.contains("bedroom") -> "bedroom $defaultName"
            text.contains("kitchen") -> "kitchen $defaultName"
            text.contains("study") -> "desk lamp"
            else -> defaultName
        }
    }

    // ==========================================
    // NEW AI FEATURES: Multimodal Vision & Tools
    // ==========================================

    /**
     * Converts a Bitmap to JPEG Base64 with smart downscaling for fast upload.
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Multimodal Visual Intelligence: Analyzes an image using Gemini 3.5 Flash.
     */
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String = "What is in this image? Explain simply."): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Image loaded successfully (${bitmap.width}x${bitmap.height}). Note: Add your Gemini API Key in Settings to enable live neural visual reasoning."
        }

        try {
            val base64Data = bitmapToBase64(bitmap)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are Nexora AI visual intelligence. Respond in natural, helpful Hinglish/English (2-3 sentences max). Question: $prompt")
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Data)
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext "Vision analysis error: HTTP ${response.code}"
            }

            val jsonResp = JSONObject(respBody)
            val candidate = jsonResp.optJSONArray("candidates")?.optJSONObject(0)
            val text = candidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")

            text?.trim()?.ifEmpty { "Tasveer me koi clear object nahi dikh raha." }
                ?: "Tasveer analyze nahi ho saki."
        } catch (e: Exception) {
            "Vision analysis error: ${e.localizedMessage ?: e.message}"
        }
    }

    /**
     * Generates a personalized daily briefing.
     */
    suspend fun generateDailyBriefing(): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Subah Bakhair! Aaj ka din naye iradon aur energy ke saath shuru karein. Apne important tasks prioritize karein aur stay positive. Have a great day!"
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Give an energetic, polite 3-sentence morning briefing in natural Hinglish. Include: 1 cheerful greeting, 1 positive thought/quote, and 1 practical productivity tip. Maximum 40 words.")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonResp = JSONObject(respBody)
                val text = jsonResp.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) return@withContext text.trim()
            }
        } catch (e: Exception) {
            // Fallback
        }

        "Good morning! Aaj ka din nayi umeedon ka hai. Apne goal par focus rakhein aur calm rahein. All the best!"
    }

    /**
     * AI Message & Note Crafter: Drafts professional, apologetic, or polite messages.
     */
    suspend fun craftSmartMessage(scenario: String, contextDetails: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext when {
                scenario.contains("late", ignoreCase = true) ->
                    "Hi, traffic ki wajah se thoda late ho raha hu, lagbhag 15 minute me pahunchta hu."
                scenario.contains("leave", ignoreCase = true) || scenario.contains("sick", ignoreCase = true) ->
                    "Salam, tabiyat theek na hone ki wajah se me aaj leave par rahunga. Urgent kaam ke liye call kar sakte hain."
                scenario.contains("thanks", ignoreCase = true) ->
                    "Thank you so much aapki madad ke liye! Bahut appreciate karta hu."
                else ->
                    "Hi, umeed hai sab theek hai. $contextDetails"
            }
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val prompt = "Draft a ready-to-send 1-2 sentence message in Hinglish/English for: '$scenario'. Additional details: '$contextDetails'. Return ONLY the message text without subject, quotes, or placeholders."

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonResp = JSONObject(respBody)
                val text = jsonResp.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) return@withContext text.trim()
            }
        } catch (e: Exception) {
            // Fallback
        }

        "Hi, me jaldi hi connect karta hu."
    }

    /**
     * AI Instant Translator between English, Hindi, and Urdu.
     */
    suspend fun translateSmart(text: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Translation of '$text' into $targetLanguage: [Offline Preview - add Gemini API key for complete translation]"
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val prompt = "Translate this text into $targetLanguage. Provide a direct, natural translation in 1-2 lines with clear meaning:\n$text"

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonResp = JSONObject(respBody)
                val output = jsonResp.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                if (!output.isNullOrBlank()) return@withContext output.trim()
            }
        } catch (e: Exception) {
            // Fallback
        }

        "Translation: $text"
    }
}
