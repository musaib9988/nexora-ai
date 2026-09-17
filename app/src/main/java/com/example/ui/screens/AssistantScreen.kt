package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlowingAiOrb
import com.example.ui.components.NexoraBrandHeader
import com.example.ui.components.OrbState
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.*

@Composable
fun AssistantScreen(
    orbState: OrbState,
    audioLevel: Float,
    statusMessage: String,
    lastUserTranscript: String,
    lastSeeruResponse: String,
    isHandsFreeActive: Boolean,
    isIotModeEnabled: Boolean = false,
    onMicClick: () -> Unit,
    onQuickActionClick: (String) -> Unit,
    onOpenAiTools: () -> Unit = {},
    onConfirmAction: (Boolean) -> Unit,
    pendingConfirmationMessage: String?
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Bar: Connection & Hands-free badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isIotModeEnabled) SuccessGreen else CyanPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isIotModeEnabled) "ESP32 SMART HOME LINKED" else "PERSONAL AI ASSISTANT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isHandsFreeActive) CyanPrimary.copy(alpha = 0.15f) else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (isHandsFreeActive) CyanPrimary else DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isHandsFreeActive) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                            contentDescription = "Hands-free status",
                            tint = if (isHandsFreeActive) CyanPrimary else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isHandsFreeActive) "Hey Seeru Active" else "Wake Word Off",
                            fontSize = 11.sp,
                            color = if (isHandsFreeActive) CyanPrimary else TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Nexora Official Brand Header with Emblem & Creator signature
            NexoraBrandHeader(compact = true, emblemSize = 64.dp)

            Spacer(modifier = Modifier.height(14.dp))

            // Glowing AI Orb (Listening, Thinking, Speaking, Idle)
            GlowingAiOrb(
                state = orbState,
                audioLevel = audioLevel,
                size = 200.dp,
                onClick = onMicClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // State & Status Caption
            Text(
                text = when (orbState) {
                    OrbState.LISTENING -> "Listening to your voice..."
                    OrbState.THINKING -> "Thinking with Gemini AI..."
                    OrbState.SPEAKING -> "Nexora speaking..."
                    OrbState.IDLE -> "Tap orb or say \"Hey Nexora\""
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = when (orbState) {
                    OrbState.LISTENING -> CyanPrimary
                    OrbState.THINKING -> VioletSecondary
                    OrbState.SPEAKING -> CyanGlow
                    OrbState.IDLE -> TextSecondary
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Voice Waveform Visualizer
            WaveformVisualizer(
                isActive = (orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING),
                audioLevel = audioLevel,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Conversation Card
            if (lastUserTranscript.isNotBlank() || lastSeeruResponse.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (lastUserTranscript.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "User voice",
                                    tint = CyanPrimary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "You said:",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "\"$lastUserTranscript\"",
                                        fontSize = 15.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (lastUserTranscript.isNotBlank() && lastSeeruResponse.isNotBlank()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = DarkCardBorder
                            )
                        }

                        if (lastSeeruResponse.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Seeru AI",
                                    tint = VioletSecondary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "NEXORA AI:",
                                        fontSize = 12.sp,
                                        color = VioletSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = lastSeeruResponse,
                                        fontSize = 15.sp,
                                        color = CyanGlow,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Microphone Action Button
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (orbState == OrbState.LISTENING) CyanPrimary else DarkSurfaceElevated
                    )
                    .border(
                        2.dp,
                        if (orbState == OrbState.LISTENING) Color.White else CyanPrimary,
                        CircleShape
                    )
                    .testTag("assistant_mic_button")
            ) {
                Icon(
                    imageVector = if (orbState == OrbState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Assistant Microphone",
                    tint = if (orbState == OrbState.LISTENING) Color(0xFF0F172A) else CyanPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Studio & Vision Tools Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VioletSecondary.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, VioletSecondary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAiTools() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(VioletSecondary.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "✨ Nexora AI Studio",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Photo Vision • Briefing • Smart SMS Crafter",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open AI Studio",
                        tint = CyanPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Voice Action Chips Header
            Text(
                text = if (isIotModeEnabled) "Smart Home & Voice Actions" else "Siri-Style Personal Voice Actions",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick action chips grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isIotModeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Default.Lightbulb,
                            label = "Room light on karo",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("room ki light on karo") }
                        )
                        QuickActionChip(
                            icon = Icons.Default.Lightbulb,
                            label = "Light band karo",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("bedroom light off karo") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Default.Thermostat,
                            label = "Temperature kya hai?",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("bedroom ka temperature kya hai?") }
                        )
                        QuickActionChip(
                            icon = Icons.Default.PowerSettingsNew,
                            label = "Saari lights band",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("saari lights band karo") }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Default.Phone,
                            label = "Call Ammi",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("Ammi ko call lagao") }
                        )
                        QuickActionChip(
                            icon = Icons.Default.PhotoCamera,
                            label = "Camera kholo",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("camera kholo") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Default.PlayArrow,
                            label = "Gaana bajao",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("gaana bajao") }
                        )
                        QuickActionChip(
                            icon = Icons.Default.Alarm,
                            label = "Alarms dikhao",
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickActionClick("alarm dikhao") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip(
                        icon = Icons.Default.SmartDisplay,
                        label = "Open YouTube",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickActionClick("open youtube") }
                    )
                    QuickActionChip(
                        icon = Icons.Default.Schedule,
                        label = "Kya time hua hai?",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickActionClick("kya time hua hai?") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sensitive Action Confirmation Dialog (Calls / SMS / Critical Hardware)
        if (pendingConfirmationMessage != null) {
            AlertDialog(
                onDismissRequest = { onConfirmAction(false) },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Confirmation",
                            tint = WarningAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Permission Confirmation",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Text(
                        text = pendingConfirmationMessage,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmAction(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Confirm", color = Color(0xFF0B0E17), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { onConfirmAction(false) }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}
