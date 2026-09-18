package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SeeruDao {
    // --- Devices ---
    @Query("SELECT * FROM devices ORDER BY room ASC, name ASC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    suspend fun getAllDevicesSnapshot(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(room) LIKE '%' || LOWER(:query) || '%' LIMIT 1")
    suspend fun findDeviceByQuery(query: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE LOWER(room) = LOWER(:room)")
    suspend fun getDevicesInRoom(room: String): List<DeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<DeviceEntity>)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Delete
    suspend fun deleteDevice(device: DeviceEntity)

    @Query("UPDATE devices SET state = :state, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateDeviceState(id: Long, state: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE devices SET isOnline = :isOnline, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateDeviceOnlineStatus(id: Long, isOnline: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE devices SET temperature = :temp, humidity = :hum, isOnline = 1, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateSensorReading(id: Long, temp: Float, hum: Float, timestamp: Long = System.currentTimeMillis())

    // --- Conversations ---
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT 60")
    fun getRecentConversations(): Flow<List<ConversationEntity>>

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    // --- Routines ---
    @Query("SELECT * FROM routines")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines")
    suspend fun getAllRoutinesSnapshot(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    // --- Contact Aliases ---
    @Query("SELECT * FROM contact_aliases ORDER BY alias ASC")
    fun getAllContactAliases(): Flow<List<ContactAliasEntity>>

    @Query("SELECT * FROM contact_aliases WHERE LOWER(alias) = LOWER(:alias) LIMIT 1")
    suspend fun findContactByAlias(alias: String): ContactAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactAlias(alias: ContactAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactAliases(aliases: List<ContactAliasEntity>)

    @Update
    suspend fun updateContactAlias(alias: ContactAliasEntity)

    @Query("UPDATE contact_aliases SET alias = LOWER(:newAlias), actualName = :newName, phoneNumber = :newPhone WHERE LOWER(alias) = LOWER(:oldAlias)")
    suspend fun updateContactByAlias(oldAlias: String, newAlias: String, newName: String, newPhone: String)

    @Delete
    suspend fun deleteContactAlias(alias: ContactAliasEntity)

    @Query("DELETE FROM contact_aliases WHERE LOWER(alias) = LOWER(:alias)")
    suspend fun deleteContactByAliasName(alias: String)

    // --- Inbuilt Notes & To-Dos App ---
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAllNotesSnapshot(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE category = 'TODO' ORDER BY isCompleted ASC, timestamp DESC")
    fun getTodos(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE category = 'NOTE' ORDER BY timestamp DESC")
    fun getRegularNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(content) LIKE '%' || LOWER(:query) || '%' ORDER BY timestamp DESC")
    suspend fun searchNotes(query: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' LIMIT 1")
    suspend fun findNoteByTitle(query: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE LOWER(content) LIKE '%' || LOWER(:query) || '%' LIMIT 1")
    suspend fun findNoteByContent(query: String): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNotesSnapshot(limit: Int = 5): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE category = 'TODO' AND isCompleted = 0 ORDER BY timestamp DESC")
    suspend fun getPendingTodosSnapshot(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET content = :newContent, timestamp = :timestamp WHERE id = :id")
    suspend fun updateNoteContent(id: Long, newContent: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateTodoStatus(id: Long, isCompleted: Boolean)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("DELETE FROM notes WHERE LOWER(title) LIKE '%' || LOWER(:titleQuery) || '%'")
    suspend fun deleteNoteByTitle(titleQuery: String): Int
}
