package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ai.AssistantOutcome
import com.example.ai.GeminiAssistant
import com.example.data.local.*
import com.example.iot.DeviceActionResult
import com.example.iot.Esp32Client
import com.example.phone.PhoneActionHandler
import com.example.service.SeeruForegroundService
import com.example.speech.SpeechManager
import com.example.ui.components.AiToolsModalBottomSheet
import com.example.ui.components.OrbState
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var database: SeeruDatabase
    private lateinit var esp32Client: Esp32Client
    private lateinit var geminiAssistant: GeminiAssistant
    private lateinit var speechManager: SpeechManager
    private lateinit var phoneActionHandler: PhoneActionHandler

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Auto-trigger voice listening when wake word received from service
            runOnUiThread {
                triggerVoiceInput()
            }
        }
    }

    private var triggerVoiceInputAction: (() -> Unit)? = null

    private fun triggerVoiceInput() {
        triggerVoiceInputAction?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = SeeruDatabase.getDatabase(applicationContext)
        esp32Client = Esp32Client()
        geminiAssistant = GeminiAssistant()
        speechManager = SpeechManager(applicationContext)
        phoneActionHandler = PhoneActionHandler(applicationContext)

        val filter = IntentFilter(SeeruForegroundService.BROADCAST_WAKE_WORD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, filter)
        }

        if (intent?.getBooleanExtra("EXTRA_AUTO_LISTEN", false) == true) {
            window.decorView.postDelayed({
                triggerVoiceInput()
            }, 600)
        }

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                SeeruMainContent(
                    database = database,
                    esp32Client = esp32Client,
                    geminiAssistant = geminiAssistant,
                    speechManager = speechManager,
                    phoneActionHandler = phoneActionHandler,
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = { isDarkTheme = it },
                    registerVoiceTrigger = { action -> triggerVoiceInputAction = action }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("EXTRA_AUTO_LISTEN", false)) {
            runOnUiThread {
                triggerVoiceInput()
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(wakeWordReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        speechManager.destroy()
        super.onDestroy()
    }
}

@Composable
fun SeeruMainContent(
    database: SeeruDatabase,
    esp32Client: Esp32Client,
    geminiAssistant: GeminiAssistant,
    speechManager: SpeechManager,
    phoneActionHandler: PhoneActionHandler,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    registerVoiceTrigger: (() -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = database.seeruDao()

    // Persistent preferences
    val prefs = remember { context.getSharedPreferences("seeru_prefs", Context.MODE_PRIVATE) }
    var isIotModeEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_iot_mode_enabled", false))
    }

    // Navigation state
    var selectedTab by remember { mutableIntStateOf(0) }

    // Live state from Room database
    val devices by dao.getAllDevices().collectAsState(initial = emptyList())
    val conversations by dao.getRecentConversations().collectAsState(initial = emptyList())
    val routines by dao.getAllRoutines().collectAsState(initial = emptyList())
    val contactAliases by dao.getAllContactAliases().collectAsState(initial = emptyList())

    // Assistant UI state
    var orbState by remember { mutableStateOf(OrbState.IDLE) }
    val isListening by speechManager.isListening.collectAsState()
    val isSpeaking by speechManager.isSpeaking.collectAsState()
    val audioRms by speechManager.audioRms.collectAsState()

    var statusMessage by remember { mutableStateOf("Say \"Hey Seeru\" or tap the orb") }
    var lastUserTranscript by remember { mutableStateOf("") }
    var lastSeeruResponse by remember { mutableStateOf("") }

    // Hands-free & Service settings
    var isHandsFreeActive by remember {
        mutableStateOf(prefs.getBoolean("pref_hands_free_active", false))
    }
    var wakeSensitivity by remember {
        mutableFloatStateOf(prefs.getFloat("pref_wake_sensitivity", 0.7f))
    }

    // Confirmation dialog state
    var pendingConfirmationMessage by remember { mutableStateOf<String?>(null) }
    var pendingActionToConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }

    // AI Studio Tools modal sheet state
    var showAiToolsSheet by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val recordAudioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted && isHandsFreeActive) {
            SeeruForegroundService.startService(context)
        }
    }

    // Sync orb state with speech manager
    LaunchedEffect(isListening, isSpeaking) {
        orbState = when {
            isListening -> OrbState.LISTENING
            isSpeaking -> OrbState.SPEAKING
            orbState == OrbState.THINKING -> OrbState.THINKING
            else -> OrbState.IDLE
        }
    }

    // Command router function
    fun executeCommand(userText: String) {
        if (userText.isBlank()) return

        lastUserTranscript = userText
        orbState = OrbState.THINKING
        statusMessage = "Analyzing with Gemini..."

        val lower = userText.lowercase().trim()

        // Quick AI Feature: Daily Briefing
        if (lower.contains("briefing") || lower.contains("morning update") || lower.contains("aaj ka din")) {
            coroutineScope.launch {
                dao.insertConversation(ConversationEntity(role = "USER", text = userText))
                val briefing = geminiAssistant.generateDailyBriefing()
                lastSeeruResponse = briefing
                speechManager.speak(briefing)
                dao.insertConversation(
                    ConversationEntity(
                        role = "ASSISTANT",
                        text = briefing,
                        actionType = "AI Daily Briefing"
                    )
                )
                orbState = OrbState.SPEAKING
            }
            return
        }

        // Quick AI Feature: AI Tools / Vision
        if (lower.contains("ai studio") || lower.contains("ai tool") || lower.contains("vision") || lower.contains("photo analyze") || lower.contains("tasveer")) {
            coroutineScope.launch {
                dao.insertConversation(ConversationEntity(role = "USER", text = userText))
                val reply = "Opening Nexora AI Studio & Vision Tools."
                lastSeeruResponse = reply
                speechManager.speak(reply)
                showAiToolsSheet = true
                dao.insertConversation(
                    ConversationEntity(
                        role = "ASSISTANT",
                        text = reply,
                        actionType = "AI Studio"
                    )
                )
                orbState = OrbState.IDLE
            }
            return
        }

        coroutineScope.launch {
            // Save user message to Room
            dao.insertConversation(
                ConversationEntity(
                    role = "USER",
                    text = userText
                )
            )

            val deviceNames = if (isIotModeEnabled) devices.map { "${it.room} ${it.name}" } else emptyList()
            val outcome = geminiAssistant.processCommand(userText, deviceNames, isIotModeEnabled)

            when (outcome) {
                is AssistantOutcome.SpokenResponse -> {
                    lastSeeruResponse = outcome.replyText
                    speechManager.speak(outcome.replyText)
                    dao.insertConversation(
                        ConversationEntity(
                            role = "ASSISTANT",
                            text = outcome.replyText
                        )
                    )
                    orbState = OrbState.SPEAKING
                }

                is AssistantOutcome.DeviceAction -> {
                    if (!isIotModeEnabled) {
                        val reply = "IoT & Smart Home mode abhi disabled hai. Lights aur smart home devices control karne ke liye Settings me 'Enable IoT Mode' on karein."
                        lastSeeruResponse = reply
                        speechManager.speak(reply)
                        dao.insertConversation(
                            ConversationEntity(
                                role = "ASSISTANT",
                                text = reply
                            )
                        )
                        orbState = OrbState.SPEAKING
                    } else {
                        val targetDev = dao.findDeviceByQuery(outcome.deviceName)
                            ?: devices.firstOrNull { it.deviceType == "LIGHT" }

                        if (targetDev != null) {
                            statusMessage = "Sending ${outcome.action} to ${targetDev.name}..."
                            val result: DeviceActionResult = esp32Client.sendDeviceCommand(
                                ip = targetDev.ipAddress,
                                port = targetDev.port,
                                pin = targetDev.targetPin,
                                action = outcome.action,
                                token = targetDev.authToken
                            )

                            val reply = if (result.isSuccess) {
                                dao.updateDeviceState(targetDev.id, result.newState)
                                dao.updateDeviceOnlineStatus(targetDev.id, true)
                                "${targetDev.name} ${if (result.newState) "chalu" else "band"} ho gaya hai."
                            } else {
                                dao.updateDeviceOnlineStatus(targetDev.id, false)
                                "${targetDev.name} se connect nahi ho paya. Device offline lag raha hai."
                            }

                            lastSeeruResponse = reply
                            speechManager.speak(reply)
                            dao.insertConversation(
                                ConversationEntity(
                                    role = "ASSISTANT",
                                    text = reply,
                                    actionType = "${targetDev.name} -> ${outcome.action}"
                                )
                            )
                            orbState = OrbState.SPEAKING
                        } else {
                            val reply = "Mujhe '${outcome.deviceName}' naam ka device nahi mila."
                            lastSeeruResponse = reply
                            speechManager.speak(reply)
                            orbState = OrbState.IDLE
                        }
                    }
                }

                is AssistantOutcome.SensorQuery -> {
                    if (!isIotModeEnabled) {
                        val reply = "IoT & Smart Home mode abhi disabled hai. Sensor readings ke liye Settings me 'Enable IoT Mode' on karein."
                        lastSeeruResponse = reply
                        speechManager.speak(reply)
                        dao.insertConversation(
                            ConversationEntity(
                                role = "ASSISTANT",
                                text = reply
                            )
                        )
                        orbState = OrbState.SPEAKING
                    } else {
                        val sensorDev = devices.firstOrNull { it.deviceType == "SENSOR" }
                        if (sensorDev != null) {
                            statusMessage = "Reading sensor from ${sensorDev.room}..."
                            val result = esp32Client.querySensor(
                                ip = sensorDev.ipAddress,
                                port = sensorDev.port,
                                token = sensorDev.authToken
                            )

                            val reply = if (result.isSuccess && result.temperature != null) {
                                dao.updateSensorReading(sensorDev.id, result.temperature, result.humidity ?: 50f)
                                "${sensorDev.room} me temperature ${result.temperature} degree Celsius aur humidity ${result.humidity}% hai."
                            } else {
                                val temp = sensorDev.temperature ?: 26.5f
                                "${sensorDev.room} me temperature lagbhag $temp degree Celsius hai."
                            }

                            lastSeeruResponse = reply
                            speechManager.speak(reply)
                            dao.insertConversation(
                                ConversationEntity(
                                    role = "ASSISTANT",
                                    text = reply,
                                    actionType = "Sensor Query"
                                )
                            )
                            orbState = OrbState.SPEAKING
                        } else {
                            val reply = "Koi temperature sensor configure nahi hai."
                            lastSeeruResponse = reply
                            speechManager.speak(reply)
                            orbState = OrbState.IDLE
                        }
                    }
                }

                is AssistantOutcome.CallAction -> {
                    val alias = dao.findContactByAlias(outcome.contactName.lowercase())
                    val displayName = alias?.actualName ?: outcome.contactName
                    val phoneNum = alias?.phoneNumber ?: outcome.contactName

                    pendingConfirmationMessage = "Kya aap $displayName ($phoneNum) ko call lagana chahte hain?"
                    pendingActionToConfirm = {
                        phoneActionHandler.dialContact(phoneNum)
                        val reply = "$displayName ko call milaya ja raha hai."
                        speechManager.speak(reply)
                        lastSeeruResponse = reply
                    }
                    orbState = OrbState.IDLE
                }

                is AssistantOutcome.MessageAction -> {
                    val alias = dao.findContactByAlias(outcome.recipient.lowercase())
                    val displayName = alias?.actualName ?: outcome.recipient.ifEmpty { "Contact" }
                    val phoneNum = alias?.phoneNumber ?: outcome.recipient

                    val reply = if (outcome.messageBody.isNotBlank()) {
                        "$displayName ko message bheja ja raha hai: \"${outcome.messageBody}\""
                    } else {
                        "SMS app open kiya ja raha hai."
                    }
                    speechManager.speak(reply)
                    lastSeeruResponse = reply

                    dao.insertConversation(
                        ConversationEntity(
                            role = "ASSISTANT",
                            text = reply,
                            actionType = "SMS: $displayName"
                        )
                    )

                    val opened = phoneActionHandler.composeSms(phoneNum, outcome.messageBody)
                    if (!opened) {
                        Toast.makeText(context, "Koi SMS app nahi mili", Toast.LENGTH_SHORT).show()
                    }
                    orbState = OrbState.IDLE
                }

                is AssistantOutcome.CameraAction -> {
                    val opened = phoneActionHandler.openCamera()
                    val reply = if (opened) "Camera open kar diya gaya hai." else "Camera app nahi mila."
                    speechManager.speak(reply)
                    lastSeeruResponse = reply

                    dao.insertConversation(
                        ConversationEntity(
                            role = "ASSISTANT",
                            text = reply,
                            actionType = "Camera"
                        )
                    )
                    orbState = OrbState.IDLE
                }

                is AssistantOutcome.MusicAction -> {
                    phoneActionHandler.controlMedia(outcome.command, outcome.songQuery)
                    val reply = if (outcome.command == "PLAY") {
                        if (outcome.songQuery.isNotBlank()) {
                            "${outcome.songQuery} play kiya ja raha hai."
                        } else {
                            "Gaana bajna shuru ho gaya hai."
                        }
                    } else {
                        "Music pause kar diya gaya hai."
                    }
                    speechManager.speak(reply)
                    lastSeeruResponse = reply

                    dao.insertConversation(
                        ConversationEntity(
                            role = "ASSISTANT",
                            text = reply,
                            actionType = "Music: ${outcome.command}"
                        )
                    )
                    orbState = OrbState.IDLE
                }

                is AssistantOutcome.AlarmAction -> {
                    phoneActionHandler.openAlarms()
                    val reply = "Alarms open kar diya gaya hai."
                    speechManager.speak(reply)
                    lastSeeruResponse = reply
                    orbState = OrbState.IDLE
                }

                is AssistantOutcome.AppAction -> {
                    val opened = phoneActionHandler.openApp(outcome.appName)
                    val reply = if (opened) {
                        "${outcome.appName} khola ja raha hai."
                    } else {
                        "Mujhe ${outcome.appName} app nahi mila."
                    }
                    speechManager.speak(reply)
                    lastSeeruResponse = reply
                    orbState = OrbState.IDLE
                }

                is AssistantOutcome.RoutineAction -> {
                    if (!isIotModeEnabled) {
                        val reply = "IoT & Smart Home mode abhi disabled hai. Routines ke liye Settings me 'Enable IoT Mode' on karein."
                        lastSeeruResponse = reply
                        speechManager.speak(reply)
                        dao.insertConversation(
                            ConversationEntity(
                                role = "ASSISTANT",
                                text = reply
                            )
                        )
                        orbState = OrbState.SPEAKING
                    } else {
                        val targetRoutine = routines.firstOrNull { it.name.equals(outcome.routineName, ignoreCase = true) }
                        val scope = targetRoutine?.targetScope ?: "ALL_DEVICES"
                        val action = targetRoutine?.targetAction ?: "OFF"

                        devices.forEach { dev ->
                            if (dev.deviceType != "SENSOR") {
                                coroutineScope.launch(Dispatchers.IO) {
                                    esp32Client.sendDeviceCommand(
                                        ip = dev.ipAddress,
                                        port = dev.port,
                                        pin = dev.targetPin,
                                        action = action,
                                        token = dev.authToken
                                    )
                                    dao.updateDeviceState(dev.id, action == "ON")
                                }
                            }
                        }

                        val reply = "${outcome.routineName} routine execute ho gaya hai. Sabhi devices $action ho gaye hain."
                        lastSeeruResponse = reply
                        speechManager.speak(reply)
                        dao.insertConversation(
                            ConversationEntity(
                                role = "ASSISTANT",
                                text = reply,
                                actionType = "Routine: ${outcome.routineName}"
                            )
                        )
                        orbState = OrbState.SPEAKING
                    }
                }

                is AssistantOutcome.TimeDateAction -> {
                    val now = java.text.SimpleDateFormat("hh:mm a, EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
                    val reply = "Abhi samay $now hai."
                    lastSeeruResponse = reply
                    speechManager.speak(reply)
                    orbState = OrbState.SPEAKING
                }

                is AssistantOutcome.Error -> {
                    lastSeeruResponse = outcome.message
                    speechManager.speak(outcome.message)
                    orbState = OrbState.IDLE
                }
            }
        }
    }

    // Register trigger callback for background wake-word
    LaunchedEffect(Unit) {
        registerVoiceTrigger {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                speechManager.startListening { spokenText ->
                    executeCommand(spokenText)
                }
            }
        }
    }

    // Toggle voice recording
    fun onMicButtonClicked() {
        if (isListening) {
            speechManager.stopListening()
            orbState = OrbState.IDLE
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            } else {
                speechManager.startListening { spokenText ->
                    executeCommand(spokenText)
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurfaceElevated,
                contentColor = CyanPrimary
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Assistant") },
                    label = { Text("Assistant", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanPrimary,
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                if (isIotModeEnabled) {
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Smart Home") },
                        label = { Text("Smart Home", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.15f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Chat") },
                    label = { Text("Chat", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanPrimary,
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                if (isIotModeEnabled) {
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = "Routines") },
                        label = { Text("Routines", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.15f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanPrimary,
                        selectedTextColor = CyanPrimary,
                        indicatorColor = CyanPrimary.copy(alpha = 0.15f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> AssistantScreen(
                    orbState = orbState,
                    audioLevel = audioRms,
                    statusMessage = statusMessage,
                    lastUserTranscript = lastUserTranscript,
                    lastSeeruResponse = lastSeeruResponse,
                    isHandsFreeActive = isHandsFreeActive,
                    isIotModeEnabled = isIotModeEnabled,
                    onMicClick = { onMicButtonClicked() },
                    onQuickActionClick = { cmd -> executeCommand(cmd) },
                    onOpenAiTools = { showAiToolsSheet = true },
                    onConfirmAction = { confirmed ->
                        if (confirmed) pendingActionToConfirm?.invoke()
                        pendingConfirmationMessage = null
                        pendingActionToConfirm = null
                    },
                    pendingConfirmationMessage = pendingConfirmationMessage
                )

                1 -> if (isIotModeEnabled) {
                    SmartHomeScreen(
                        devices = devices,
                        onToggleDevice = { dev ->
                            coroutineScope.launch {
                                val action = if (dev.state) "OFF" else "ON"
                                val result = esp32Client.sendDeviceCommand(
                                    ip = dev.ipAddress,
                                    port = dev.port,
                                    pin = dev.targetPin,
                                    action = action,
                                    token = dev.authToken
                                )
                                if (result.isSuccess) {
                                    dao.updateDeviceState(dev.id, result.newState)
                                    dao.updateDeviceOnlineStatus(dev.id, true)
                                } else {
                                    dao.updateDeviceOnlineStatus(dev.id, false)
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onRefreshSensor = { dev ->
                            coroutineScope.launch {
                                val res = esp32Client.querySensor(dev.ipAddress, dev.port, dev.authToken)
                                if (res.isSuccess && res.temperature != null) {
                                    dao.updateSensorReading(dev.id, res.temperature, res.humidity ?: 50f)
                                } else {
                                    Toast.makeText(context, "Sensor unreachable: ${dev.ipAddress}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onAddDevice = { dev ->
                            coroutineScope.launch { dao.insertDevice(dev) }
                        },
                        onDeleteDevice = { dev ->
                            coroutineScope.launch { dao.deleteDevice(dev) }
                        },
                        onAllDevicesOff = {
                            executeCommand("saari lights band karo")
                        }
                    )
                } else {
                    selectedTab = 0
                }

                2 -> ChatScreen(
                    conversations = conversations,
                    onSendMessage = { text -> executeCommand(text) },
                    onClearHistory = {
                        coroutineScope.launch { dao.clearConversations() }
                    },
                    onOpenAiTools = { showAiToolsSheet = true }
                )

                3 -> if (isIotModeEnabled) {
                    RoutinesScreen(
                        routines = routines,
                        onExecuteRoutine = { routine ->
                            executeCommand(routine.triggerPhrase)
                        },
                        onAddRoutine = { routine ->
                            coroutineScope.launch { dao.insertRoutine(routine) }
                        },
                        onDeleteRoutine = { routine ->
                            coroutineScope.launch { dao.deleteRoutine(routine) }
                        }
                    )
                } else {
                    selectedTab = 0
                }

                4 -> SettingsAndFirmwareScreen(
                    isHandsFreeEnabled = isHandsFreeActive,
                    onToggleHandsFree = { enable ->
                        isHandsFreeActive = enable
                        prefs.edit().putBoolean("pref_hands_free_active", enable).apply()
                        if (enable) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                SeeruForegroundService.startService(context)
                                Toast.makeText(context, "Hey Seeru background listening started", Toast.LENGTH_SHORT).show()
                            } else {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                )
                            }
                        } else {
                            SeeruForegroundService.stopService(context)
                            Toast.makeText(context, "Background listening stopped", Toast.LENGTH_SHORT).show()
                        }
                    },
                    wakeWordSensitivity = wakeSensitivity,
                    onSensitivityChange = {
                        wakeSensitivity = it
                        prefs.edit().putFloat("pref_wake_sensitivity", it).apply()
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = onToggleDarkTheme,
                    isIotModeEnabled = isIotModeEnabled,
                    onToggleIotMode = { enable ->
                        isIotModeEnabled = enable
                        prefs.edit().putBoolean("pref_iot_mode_enabled", enable).apply()
                        if (!enable && (selectedTab == 1 || selectedTab == 3)) {
                            selectedTab = 0
                        }
                    },
                    onOpenSmartHomeDashboard = {
                        selectedTab = 1
                    },
                    contactAliases = contactAliases,
                    onAddContactAlias = { alias ->
                        coroutineScope.launch { dao.insertContactAlias(alias) }
                    }
                )
            }
        }

        // AI Studio & Vision Tools Modal Bottom Sheet
        if (showAiToolsSheet) {
            AiToolsModalBottomSheet(
                geminiAssistant = geminiAssistant,
                onDismiss = { showAiToolsSheet = false },
                onSpeak = { text ->
                    statusMessage = text
                    speechManager.speak(text)
                },
                onSendSms = { messageBody ->
                    phoneActionHandler.composeSms("", messageBody)
                }
            )
        }
    }
}
