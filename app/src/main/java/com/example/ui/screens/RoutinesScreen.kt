package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RoutineEntity
import com.example.ui.theme.*

@Composable
fun RoutinesScreen(
    routines: List<RoutineEntity>,
    onExecuteRoutine: (RoutineEntity) -> Unit,
    onAddRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("ALL_DEVICES") }
    var action by remember { mutableStateOf("OFF") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = VioletSecondary,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Routine")
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
            Text(
                text = "Automation Routines",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Multi-device voice macros & automation",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onExecute = { onExecuteRoutine(routine) },
                        onDelete = { onDeleteRoutine(routine) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Create Routine", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Routine Name (e.g. Cinema Mode)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = trigger,
                            onValueChange = { trigger = it },
                            label = { Text("Trigger Phrase (e.g. movie shuru karo)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (name.isNotBlank() && trigger.isNotBlank()) {
                                onAddRoutine(
                                    RoutineEntity(
                                        name = name.trim(),
                                        triggerPhrase = trigger.trim().lowercase(),
                                        description = desc.trim(),
                                        targetScope = scope,
                                        targetAction = action
                                    )
                                )
                                showAddDialog = false
                                name = ""
                                trigger = ""
                                desc = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletSecondary)
                    ) {
                        Text("Create", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun RoutineCard(
    routine: RoutineEntity,
    onExecute: () -> Unit,
    onDelete: () -> Unit
) {
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
                        text = routine.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Trigger: \"${routine.triggerPhrase}\"",
                        fontSize = 12.sp,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onExecute,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = Color(0xFF0B0E17),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Run",
                        fontSize = 12.sp,
                        color = Color(0xFF0B0E17),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (routine.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = routine.description,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
