package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiAssistant
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class AiToolTab {
    VISION,
    BRIEFING,
    MESSAGE_DRAFTER,
    TRANSLATOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsModalBottomSheet(
    geminiAssistant: GeminiAssistant,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit,
    onSendSms: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AiToolTab.VISION) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkCardBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VioletSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Nexora AI Studio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Powered by Gemini 3.5 Flash",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tool Switcher Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = CyanPrimary,
                edgePadding = 0.dp,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == AiToolTab.VISION,
                    onClick = { selectedTab = AiToolTab.VISION },
                    text = { Text("📸 Vision AI", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == AiToolTab.BRIEFING,
                    onClick = { selectedTab = AiToolTab.BRIEFING },
                    text = { Text("🌅 Briefing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == AiToolTab.MESSAGE_DRAFTER,
                    onClick = { selectedTab = AiToolTab.MESSAGE_DRAFTER },
                    text = { Text("✍️ Message Crafter", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == AiToolTab.TRANSLATOR,
                    onClick = { selectedTab = AiToolTab.TRANSLATOR },
                    text = { Text("🌐 Translator", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Content
            when (selectedTab) {
                AiToolTab.VISION -> VisionAiTool(geminiAssistant, onSpeak)
                AiToolTab.BRIEFING -> DailyBriefingTool(geminiAssistant, onSpeak)
                AiToolTab.MESSAGE_DRAFTER -> MessageDrafterTool(geminiAssistant, onSpeak, onSendSms)
                AiToolTab.TRANSLATOR -> TranslatorTool(geminiAssistant, onSpeak)
            }
        }
    }
}

// -------------------------------------------------------------
// 1. VISION AI TOOL
// -------------------------------------------------------------
@Composable
private fun VisionAiTool(
    geminiAssistant: GeminiAssistant,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var promptQuery by remember { mutableStateOf("What is in this image? Explain simply in Hinglish.") }
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val bmp = decodeUriToBitmap(context, it)
                selectedBitmap = bmp
                resultText = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Select any photo or screenshot to analyze with Gemini visual AI:",
            fontSize = 13.sp,
            color = TextSecondary
        )

        // Image Preview or Placeholder
        if (selectedBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Selected photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Change button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                ) {
                    Text(
                        text = "Change Photo",
                        fontSize = 11.sp,
                        color = CyanPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Pick photo",
                        tint = CyanPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to Pick Photo from Gallery",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Supports objects, documents, scenes & handwriting",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Quick vision prompts
        Text(text = "Quick Questions:", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("What is this?", "Read text / OCR", "Explain simply").forEach { q ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (promptQuery == q) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (promptQuery == q) CyanPrimary else DarkCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { promptQuery = q }
                ) {
                    Text(
                        text = q,
                        fontSize = 11.sp,
                        color = if (promptQuery == q) CyanPrimary else TextSecondary,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        OutlinedTextField(
            value = promptQuery,
            onValueChange = { promptQuery = it },
            placeholder = { Text("Or ask a custom question...", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = DarkCardBorder,
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated
            ),
            singleLine = true
        )

        Button(
            onClick = {
                if (selectedBitmap != null && !isLoading) {
                    isLoading = true
                    coroutineScope.launch {
                        resultText = geminiAssistant.analyzeImage(selectedBitmap!!, promptQuery)
                        isLoading = false
                        onSpeak(resultText)
                    }
                } else if (selectedBitmap == null) {
                    Toast.makeText(context, "Pehle ek photo select karein!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedBitmap != null && !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF0B0E17)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF0B0E17))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyzing with Gemini...", fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Photo", fontWeight = FontWeight.Bold)
            }
        }

        if (resultText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VioletSecondary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, VioletSecondary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI Vision Result:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanGlow)
                        Row {
                            IconButton(onClick = { onSpeak(resultText) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Speak", tint = CyanPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Vision Result", resultText))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = resultText, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. DAILY BRIEFING TOOL
// -------------------------------------------------------------
@Composable
private fun DailyBriefingTool(
    geminiAssistant: GeminiAssistant,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var briefingText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Generate a personalized, upbeat morning briefing with daily wisdom & motivation:",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Button(
            onClick = {
                isLoading = true
                coroutineScope.launch {
                    briefingText = geminiAssistant.generateDailyBriefing()
                    isLoading = false
                    onSpeak(briefingText)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF0B0E17)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF0B0E17))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generating Briefing...", fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get Today's AI Briefing", fontWeight = FontWeight.Bold)
            }
        }

        if (briefingText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌅 Daily Briefing", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
                        Row {
                            IconButton(onClick = { onSpeak(briefingText) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = CyanPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Daily Briefing", briefingText))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = briefingText, fontSize = 14.sp, color = TextPrimary, lineHeight = 21.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. MESSAGE CRAFTER TOOL
// -------------------------------------------------------------
@Composable
private fun MessageDrafterTool(
    geminiAssistant: GeminiAssistant,
    onSpeak: (String) -> Unit,
    onSendSms: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedScenario by remember { mutableStateOf("Late Arrival") }
    var contextDetails by remember { mutableStateOf("") }
    var craftedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scenarios = listOf("Late Arrival", "Sick Leave", "Thank You", "Formal Inquiry")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Generate polished messages in Hinglish or English and send directly via SMS:",
            fontSize = 13.sp,
            color = TextSecondary
        )

        // Scenario chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scenarios.forEach { sc ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedScenario == sc) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (selectedScenario == sc) CyanPrimary else DarkCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedScenario = sc }
                ) {
                    Text(
                        text = sc,
                        fontSize = 11.sp,
                        color = if (selectedScenario == sc) CyanPrimary else TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = if (selectedScenario == sc) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        OutlinedTextField(
            value = contextDetails,
            onValueChange = { contextDetails = it },
            placeholder = { Text("Optional details (e.g. 20 min late, traffic)", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = DarkCardBorder,
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated
            ),
            singleLine = true
        )

        Button(
            onClick = {
                isLoading = true
                coroutineScope.launch {
                    craftedText = geminiAssistant.craftSmartMessage(selectedScenario, contextDetails)
                    isLoading = false
                    onSpeak(craftedText)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF0B0E17)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF0B0E17))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Drafting Message...", fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Craft Ready Message", fontWeight = FontWeight.Bold)
            }
        }

        if (craftedText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Drafted Message:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = craftedText, fontSize = 15.sp, color = TextPrimary, lineHeight = 21.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSendSms(craftedText) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send as SMS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Drafted Message", craftedText))
                                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. TRANSLATOR TOOL
// -------------------------------------------------------------
@Composable
private fun TranslatorTool(
    geminiAssistant: GeminiAssistant,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var sourceText by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("Urdu") }
    var translationResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val languages = listOf("Urdu", "Hindi", "English")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Translate phrases seamlessly between English, Hindi, and Urdu:",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            languages.forEach { lang ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (targetLanguage == lang) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (targetLanguage == lang) CyanPrimary else DarkCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { targetLanguage = lang }
                ) {
                    Text(
                        text = "To $lang",
                        fontSize = 11.sp,
                        color = if (targetLanguage == lang) CyanPrimary else TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = if (targetLanguage == lang) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        OutlinedTextField(
            value = sourceText,
            onValueChange = { sourceText = it },
            placeholder = { Text("Enter text to translate...", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = DarkCardBorder,
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated
            ),
            minLines = 2
        )

        Button(
            onClick = {
                if (sourceText.isNotBlank()) {
                    isLoading = true
                    coroutineScope.launch {
                        translationResult = geminiAssistant.translateSmart(sourceText.trim(), targetLanguage)
                        isLoading = false
                        onSpeak(translationResult)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = sourceText.isNotBlank() && !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF0B0E17)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF0B0E17))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Translating...", fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Translate", fontWeight = FontWeight.Bold)
            }
        }

        if (translationResult.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, VioletSecondary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Translation ($targetLanguage):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VioletSecondary)
                        Row {
                            IconButton(onClick = { onSpeak(translationResult) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Speak", tint = CyanPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Translation", translationResult))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = translationResult, fontSize = 15.sp, color = TextPrimary, lineHeight = 21.sp)
                }
            }
        }
    }
}

private fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}
