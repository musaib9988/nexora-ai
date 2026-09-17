package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DeviceEntity
import com.example.ui.theme.*

@Composable
fun SmartHomeScreen(
    devices: List<DeviceEntity>,
    onToggleDevice: (DeviceEntity) -> Unit,
    onRefreshSensor: (DeviceEntity) -> Unit,
    onAddDevice: (DeviceEntity) -> Unit,
    onDeleteDevice: (DeviceEntity) -> Unit,
    onAllDevicesOff: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    // Dialog form state
    var newName by remember { mutableStateOf("") }
    var newRoom by remember { mutableStateOf("Bedroom") }
    var newIp by remember { mutableStateOf("192.168.1.150") }
    var newPin by remember { mutableStateOf("2") }
    var newType by remember { mutableStateOf("LIGHT") }
    var newToken by remember { mutableStateOf("seeru_secret_token") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CyanPrimary,
                contentColor = Color(0xFF0B0E17),
                modifier = Modifier.testTag("add_device_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Device")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header with Master All Off button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Smart Home IoT",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Direct Local ESP32 Wi-Fi Control",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onAllDevicesOff,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "All Off",
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "All Off", color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No ESP32 Devices Configured",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button to add your first ESP32 board.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Group devices by Room
                val grouped = devices.groupBy { it.room }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    grouped.forEach { (room, roomDevices) ->
                        item {
                            Text(
                                text = room.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        items(roomDevices, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                onToggle = { onToggleDevice(device) },
                                onRefresh = { onRefreshSensor(device) },
                                onDelete = { onDeleteDevice(device) }
                            )
                        }
                    }
                }
            }
        }

        // Add Device Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = {
                    Text(
                        text = "Add ESP32 Device",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Device Name (e.g. Balcony Light)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newRoom,
                            onValueChange = { newRoom = it },
                            label = { Text("Room (e.g. Bedroom, Living)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newIp,
                            onValueChange = { newIp = it },
                            label = { Text("ESP32 IP Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = { newPin = it },
                                label = { Text("GPIO Pin") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newType,
                                onValueChange = { newType = it },
                                label = { Text("Type (LIGHT/FAN/SENSOR)") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank() && newIp.isNotBlank()) {
                                onAddDevice(
                                    DeviceEntity(
                                        name = newName.trim(),
                                        room = newRoom.trim(),
                                        ipAddress = newIp.trim(),
                                        targetPin = newPin.toIntOrNull() ?: 2,
                                        deviceType = newType.uppercase().trim(),
                                        authToken = newToken.trim(),
                                        isOnline = true
                                    )
                                )
                                showAddDialog = false
                                newName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Add Device", color = Color(0xFF0B0E17), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddDialog = false }) {
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
fun DeviceCard(
    device: DeviceEntity,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(
            1.dp,
            if (device.state) CyanPrimary.copy(alpha = 0.6f) else DarkCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (device.state) CyanPrimary.copy(alpha = 0.15f) else DarkSurface
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (device.deviceType) {
                                "FAN" -> Icons.Default.Air
                                "SENSOR" -> Icons.Default.Thermostat
                                "RELAY" -> Icons.Default.ElectricBolt
                                else -> Icons.Default.Lightbulb
                            },
                            contentDescription = null,
                            tint = if (device.state) CyanPrimary else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = device.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (device.isOnline) SuccessGreen else ErrorRed)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${device.ipAddress} (GPIO ${device.targetPin})",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                if (device.deviceType == "SENSOR") {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Sensor",
                            tint = CyanPrimary
                        )
                    }
                } else {
                    Switch(
                        checked = device.state,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0B0E17),
                            checkedTrackColor = CyanPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }
            }

            // Sensor Readings Display
            if (device.deviceType == "SENSOR") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkCardBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Temperature", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (device.temperature != null) "${device.temperature}°C" else "--",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Humidity", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (device.humidity != null) "${device.humidity}%" else "--",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = VioletSecondary
                        )
                    }
                }
            }
        }
    }
}
