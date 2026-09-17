package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        DeviceEntity::class,
        ConversationEntity::class,
        RoutineEntity::class,
        ContactAliasEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SeeruDatabase : RoomDatabase() {
    abstract fun seeruDao(): SeeruDao

    companion object {
        @Volatile
        private var INSTANCE: SeeruDatabase? = null

        fun getDatabase(context: Context): SeeruDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SeeruDatabase::class.java,
                    "seeru_ai.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial IoT devices and routines
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).seeruDao()
                            dao.insertDevices(
                                listOf(
                                    DeviceEntity(
                                        name = "Main Light",
                                        room = "Bedroom",
                                        ipAddress = "192.168.1.150",
                                        port = 80,
                                        deviceType = "LIGHT",
                                        state = false,
                                        targetPin = 2,
                                        authToken = "seeru_secret_token",
                                        isOnline = true
                                    ),
                                    DeviceEntity(
                                        name = "Ceiling Fan",
                                        room = "Bedroom",
                                        ipAddress = "192.168.1.150",
                                        port = 80,
                                        deviceType = "FAN",
                                        state = false,
                                        targetPin = 4,
                                        authToken = "seeru_secret_token",
                                        isOnline = true
                                    ),
                                    DeviceEntity(
                                        name = "Climate Sensor",
                                        room = "Bedroom",
                                        ipAddress = "192.168.1.150",
                                        port = 80,
                                        deviceType = "SENSOR",
                                        temperature = 26.5f,
                                        humidity = 58.0f,
                                        targetPin = 15,
                                        authToken = "seeru_secret_token",
                                        isOnline = true
                                    ),
                                    DeviceEntity(
                                        name = "Desk Lamp",
                                        room = "Study",
                                        ipAddress = "192.168.1.151",
                                        port = 80,
                                        deviceType = "LIGHT",
                                        state = false,
                                        targetPin = 2,
                                        authToken = "seeru_secret_token",
                                        isOnline = true
                                    )
                                )
                            )
                            dao.insertRoutines(
                                listOf(
                                    RoutineEntity(
                                        name = "Good Morning",
                                        triggerPhrase = "good morning",
                                        description = "Turns on bedroom light and reads climate sensor",
                                        targetScope = "BEDROOM",
                                        targetAction = "ON",
                                        isEnabled = true
                                    ),
                                    RoutineEntity(
                                        name = "All Off",
                                        triggerPhrase = "all devices off",
                                        description = "Switches off all lights and fans safely",
                                        targetScope = "ALL_DEVICES",
                                        targetAction = "OFF",
                                        isEnabled = true
                                    ),
                                    RoutineEntity(
                                        name = "Study Mode",
                                        triggerPhrase = "study mode",
                                        description = "Turns on desk lamp and study appliances",
                                        targetScope = "STUDY",
                                        targetAction = "ON",
                                        isEnabled = true
                                    )
                                )
                            )
                            dao.insertContactAliases(
                                listOf(
                                    ContactAliasEntity("ammi", "Mother", "+919876543210"),
                                    ContactAliasEntity("abbu", "Father", "+919876543211"),
                                    ContactAliasEntity("bhai", "Brother", "+919876543212")
                                )
                            )
                        }
                    }
                }).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
