package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ContactAliasEntity
import com.example.iot.Esp32FirmwareCode
import com.example.ui.components.NexoraBrandHeader
import com.example.ui.theme.*

@Composable
fun SettingsAndFirmwareScreen(
    isHandsFreeEnabled: Boolean,
    onToggleHandsFree: (Boolean) -> Unit,
    wakeWordSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    isIotModeEnabled: Boolean = false,
    onToggleIotMode: (Boolean) -> Unit = {},
    onOpenSmartHomeDashboard: (() -> Unit)? = null,
    contactAliases: List<ContactAliasEntity>,
    onAddContactAlias: (ContactAliasEntity) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showAddContactDialog by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Settings & Preferences",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = if (isIotModeEnabled) "Wake word, privacy, and ESP32 setup" else "Wake word, privacy, and voice assistant options",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 1: Wake Word & Background Service
        Text(
            text = "HEY SEERU WAKE WORD & BACKGROUND",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hands-Free Foreground Service",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Listen for \"Hey Seeru\" even when app is minimized",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isHandsFreeEnabled,
                        onCheckedChange = onToggleHandsFree,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0B0E17),
                            checkedTrackColor = CyanPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkCardBorder)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Wake Sensitivity: ${(wakeWordSensitivity * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = wakeWordSensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: AI Brain Status & Theme
        Text(
            text = "AI ENGINE & APPEARANCE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = VioletSecondary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gemini 2.5 Flash NLU",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Server-Side Gemini API + Offline Rules Active",
                            fontSize = 12.sp,
                            color = SuccessGreen
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = SuccessGreen
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkCardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Futuristic Dark Canvas",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onToggleDarkTheme,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0B0E17),
                            checkedTrackColor = VioletSecondary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: Contact Aliases (Ammi, Abbu, etc.)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PHONE CONTACT ALIASES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricBlue,
                letterSpacing = 1.sp
            )
            TextButton(onClick = { showAddContactDialog = true }) {
                Text("+ Add Alias", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (contactAliases.isEmpty()) {
                    Text(text = "No contact aliases defined.", color = TextMuted, fontSize = 13.sp)
                } else {
                    contactAliases.forEachIndexed { index, alias ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "\"${alias.alias}\" -> ${alias.actualName}",
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = alias.phoneNumber,
                                fontSize = 12.sp,
                                color = CyanPrimary
                            )
                        }
                        if (index < contactAliases.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = DarkCardBorder)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 4: IOT & SMART HOME (ADVANCED / DISCREET)
        Text(
            text = "IOT & SMART HOME (ADVANCED)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, if (isIotModeEnabled) CyanPrimary.copy(alpha = 0.5f) else DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Enable IoT Mode",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Enable ESP32 smart home devices, IoT dashboard, and automation routines",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    Switch(
                        checked = isIotModeEnabled,
                        onCheckedChange = onToggleIotMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0B0E17),
                            checkedTrackColor = CyanPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }

                if (!isIotModeEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Text(
                            text = "IoT features are disabled. Nexora operates exclusively as a Siri-style personal assistant. Turn on this toggle to connect ESP32 hardware and automation.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DarkCardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Smart Home & Routines Active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SuccessGreen
                            )
                        }

                        if (onOpenSmartHomeDashboard != null) {
                            OutlinedButton(
                                onClick = onOpenSmartHomeDashboard,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyanPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Open Dashboard", fontSize = 11.sp, color = CyanPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ESP32 Arduino Firmware Guide is only shown when IoT Mode is explicitly enabled
        if (isIotModeEnabled) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ESP32 ARDUINO C++ FIRMWARE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber,
                    letterSpacing = 1.sp
                )

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("ESP32 Firmware", Esp32FirmwareCode.ARDUINO_SKETCH)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Firmware copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF0B0E17),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Code", fontSize = 11.sp, color = Color(0xFF0B0E17), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Safety warning card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = WarningAmber.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = WarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mains Safety: When switching 110V/220V AC appliances, always use optocoupled isolated relay modules. Never touch exposed high voltage wires.",
                        fontSize = 12.sp,
                        color = WarningAmber,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Firmware Preview Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Text(
                    text = Esp32FirmwareCode.ARDUINO_SKETCH.take(450) + "\n\n// ... [Tap 'Copy Code' to get full sketch with setup() and loop()]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = CyanGlow,
                    modifier = Modifier.padding(12.dp),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: About Nexora
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NexoraBrandHeader(compact = false, emblemSize = 88.dp)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Next-Generation Personal AI Assistant & Smart Home Automation System",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Version 1.0.0 (Release Build)",
                            fontSize = 11.sp,
                            color = CyanPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Add Contact Alias") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Alias (e.g. ammi, ali, boss)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (aliasInput.isNotBlank() && phoneInput.isNotBlank()) {
                        onAddContactAlias(
                            ContactAliasEntity(
                                alias = aliasInput.trim().lowercase(),
                                actualName = nameInput.trim().ifEmpty { aliasInput.trim() },
                                phoneNumber = phoneInput.trim()
                            )
                        )
                        showAddContactDialog = false
                        aliasInput = ""
                        nameInput = ""
                        phoneInput = ""
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddContactDialog = false }) { Text("Cancel") }
            }
        )
    }
}
