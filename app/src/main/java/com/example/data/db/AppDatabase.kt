package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.MatchMode
import com.example.data.model.TargetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [VoiceRuleEntity::class, CommandLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceRuleDao(): VoiceRuleDao
    abstract fun commandLogDao(): CommandLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voice_bridge_db"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default rules
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).voiceRuleDao()
                            seedDefaultRules(dao)
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDefaultRules(dao: VoiceRuleDao) {
            dao.insertRule(
                VoiceRuleEntity(
                    name = "Flashlight Toggle",
                    triggerPhrase = "flashlight",
                    matchMode = MatchMode.CONTAINS,
                    targetApp = TargetApp.TASKER,
                    customAction = "net.dinglisch.android.tasker.ACTION_SPEECH_COMMAND",
                    customExtraKey = "command",
                    customPayload = "toggle_flashlight",
                    customTtsFeedback = "Toggling flashlight",
                    isEnabled = true
                )
            )
            dao.insertRule(
                VoiceRuleEntity(
                    name = "MacroDroid Phone Silent",
                    triggerPhrase = "silent mode",
                    matchMode = MatchMode.CONTAINS,
                    targetApp = TargetApp.MACRODROID,
                    customAction = "com.arlosoft.macrodroid.intent.action.TRIGGER",
                    customExtraKey = "command",
                    customPayload = "set_silent",
                    customTtsFeedback = "Silent mode activated",
                    isEnabled = true
                )
            )
            dao.insertRule(
                VoiceRuleEntity(
                    name = "Tasker Good Morning",
                    triggerPhrase = "good morning",
                    matchMode = MatchMode.CONTAINS,
                    targetApp = TargetApp.TASKER,
                    customAction = "net.dinglisch.android.tasker.ACTION_SPEECH_COMMAND",
                    customExtraKey = "voice_command",
                    customPayload = "morning_routine",
                    customTtsFeedback = "Good morning! Starting routine",
                    isEnabled = true
                )
            )
        }
    }
}
