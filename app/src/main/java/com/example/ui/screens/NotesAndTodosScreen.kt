package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NoteEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesAndTodosScreen(
    notes: List<NoteEntity>,
    onAddNote: (NoteEntity) -> Unit,
    onUpdateNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onToggleTodo: (Long, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, NOTE, TODO, COMPLETED
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }

    // Filter notes based on category and query
    val filteredNotes = remember(notes, searchQuery, selectedFilter) {
        notes.filter { note ->
            val matchesFilter = when (selectedFilter) {
                "NOTE" -> note.category == "NOTE"
                "TODO" -> note.category == "TODO" && !note.isCompleted
                "COMPLETED" -> note.category == "TODO" && note.isCompleted
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                note.title.contains(searchQuery, ignoreCase = true) ||
                        note.content.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    val totalNotesCount = remember(notes) { notes.count { it.category == "NOTE" } }
    val pendingTodosCount = remember(notes) { notes.count { it.category == "TODO" && !it.isCompleted } }
    val completedTodosCount = remember(notes) { notes.count { it.category == "TODO" && it.isCompleted } }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Notes & Tasks",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Inbuilt Notepad & To-Dos with Voice Sync",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            editingNote = null
                            showAddEditDialog = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CyanPrimary.copy(alpha = 0.2f),
                            contentColor = CyanPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_note_header_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Hint Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice Tip",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Say: \"Note banao meeting kal 10 baje hai\" ya \"Add todo buy milk\"",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your notes and to-dos...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterTabChip(
                            label = "All (${notes.size})",
                            isSelected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" }
                        )
                    }
                    item {
                        FilterTabChip(
                            label = "Notes ($totalNotesCount)",
                            isSelected = selectedFilter == "NOTE",
                            onClick = { selectedFilter = "NOTE" }
                        )
                    }
                    item {
                        FilterTabChip(
                            label = "To-Dos ($pendingTodosCount)",
                            isSelected = selectedFilter == "TODO",
                            onClick = { selectedFilter = "TODO" }
                        )
                    }
                    item {
                        FilterTabChip(
                            label = "Done ($completedTodosCount)",
                            isSelected = selectedFilter == "COMPLETED",
                            onClick = { selectedFilter = "COMPLETED" }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showAddEditDialog = true
                },
                containerColor = CyanPrimary,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_add_note")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Add Note or To-Do")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (filteredNotes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAlt,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No notes match '$searchQuery'" else "No notes or tasks here yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create one manually using '+' or ask Nexora via voice:\n\"Hey Nexora, save note meeting kal 10 baje\"",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCardItem(
                            note = note,
                            onToggleTodo = { onToggleTodo(note.id, !note.isCompleted) },
                            onEdit = {
                                editingNote = note
                                showAddEditDialog = true
                            },
                            onDelete = {
                                noteToDelete = note
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Clearance for FAB
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddEditDialog) {
        AddEditNoteDialog(
            initialNote = editingNote,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { note ->
                if (editingNote == null) {
                    onAddNote(note)
                } else {
                    onUpdateNote(note)
                }
                showAddEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note?", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete '${noteToDelete?.title}'? This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        noteToDelete?.let { onDeleteNote(it) }
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@Composable
private fun FilterTabChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated,
        border = BorderStroke(1.dp, if (isSelected) CyanPrimary else DarkCardBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) CyanPrimary else TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun NoteCardItem(
    note: NoteEntity,
    onToggleTodo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = remember(note.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(note.colorHex)).copy(alpha = 0.25f)
        } catch (e: Exception) {
            DarkSurfaceElevated
        }
    }

    val timeFormatted = remember(note.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(note.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, if (note.isCompleted) DarkCardBorder.copy(alpha = 0.5f) else DarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("note_card_${note.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.category == "TODO") {
                        IconButton(
                            onClick = onToggleTodo,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("todo_checkbox_${note.id}")
                        ) {
                            Icon(
                                imageVector = if (note.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = "Toggle To-Do",
                                tint = if (note.isCompleted) SuccessGreen else CyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = if (note.category == "TODO") "TO-DO" else "NOTE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (note.category == "TODO") VioletSecondary else CyanPrimary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit note",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete note",
                            tint = ErrorRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (note.isCompleted) TextMuted else TextPrimary,
                textDecoration = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (note.content.isNotBlank() && note.content != note.title) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    fontSize = 13.sp,
                    color = if (note.isCompleted) TextMuted.copy(alpha = 0.6f) else TextSecondary,
                    textDecoration = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddEditNoteDialog(
    initialNote: NoteEntity?,
    onDismiss: () -> Unit,
    onConfirm: (NoteEntity) -> Unit
) {
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var category by remember { mutableStateOf(initialNote?.category ?: "NOTE") }
    var colorHex by remember { mutableStateOf(initialNote?.colorHex ?: "#1E293B") }

    val colorPresets = listOf(
        "#1E293B", // Dark Slate
        "#0E7490", // Cyan Dark
        "#0F766E", // Emerald Dark
        "#6D28D9", // Purple Dark
        "#B45309", // Amber Dark
        "#BE123C"  // Rose Dark
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialNote == null) "New Note or Task" else "Edit Note",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Type Selector: Note or To-Do
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { category = "NOTE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (category == "NOTE") CyanPrimary else DarkSurfaceElevated,
                            contentColor = if (category == "NOTE") Color.Black else TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📝 Note", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { category = "TODO" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (category == "TODO") VioletSecondary else DarkSurfaceElevated,
                            contentColor = if (category == "TODO") Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✅ To-Do", fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input_title")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content / Details") },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input_content")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Color Picker Chips
                Text("Card Color:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorPresets.forEach { hex ->
                        val parsedColor = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .clickable { colorHex = hex }
                                .then(
                                    if (colorHex == hex) Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() && content.isBlank()) return@Button
                    val finalTitle = title.ifBlank { content.take(24).replaceFirstChar { it.uppercase() } }
                    val newNote = (initialNote?.copy(
                        title = finalTitle,
                        content = content,
                        category = category,
                        colorHex = colorHex,
                        timestamp = System.currentTimeMillis()
                    )) ?: NoteEntity(
                        title = finalTitle,
                        content = content,
                        category = category,
                        colorHex = colorHex,
                        isCompleted = false
                    )
                    onConfirm(newNote)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black),
                modifier = Modifier.testTag("btn_save_note")
            ) {
                Text(if (initialNote == null) "Save" else "Update", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
