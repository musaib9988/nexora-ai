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
    @Query("SELECT * FROM contact_aliases")
    fun getAllContactAliases(): Flow<List<ContactAliasEntity>>

    @Query("SELECT * FROM contact_aliases WHERE LOWER(alias) = LOWER(:alias) LIMIT 1")
    suspend fun findContactByAlias(alias: String): ContactAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactAlias(alias: ContactAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactAliases(aliases: List<ContactAliasEntity>)

    @Delete
    suspend fun deleteContactAlias(alias: ContactAliasEntity)
}
