package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val room: String,
    val ipAddress: String,
    val port: Int = 80,
    val deviceType: String, // "LIGHT", "FAN", "RELAY", "SENSOR", "CUSTOM"
    val state: Boolean = false,
    val targetPin: Int = 2,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val authToken: String = "seeru_secret_token",
    val isOnline: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "USER" or "ASSISTANT"
    val text: String,
    val actionType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val triggerPhrase: String,
    val description: String,
    val targetScope: String, // "ALL_LIGHTS", "ALL_DEVICES", "BEDROOM", "STUDY"
    val targetAction: String, // "ON" or "OFF"
    val isEnabled: Boolean = true
)

@Entity(tableName = "contact_aliases")
data class ContactAliasEntity(
    @PrimaryKey
    val alias: String, // e.g. "ammi", "abbu", "bhai", "ali"
    val actualName: String,
    val phoneNumber: String
)
