package com.mario.luna.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("luna_settings", Context.MODE_PRIVATE)
    
    private val _backgroundPlayEnabled = MutableStateFlow(prefs.getBoolean(KEY_BG_PLAY, true))
    val backgroundPlayEnabled: StateFlow<Boolean> = _backgroundPlayEnabled.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER_NAME, "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BG_PLAY, enabled).apply()
        _backgroundPlayEnabled.value = enabled
    }

    fun isBackgroundPlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_BG_PLAY, true)
    }

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
        _userName.value = name
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    companion object {
        private const val KEY_BG_PLAY = "background_play_enabled"
        private const val KEY_USER_NAME = "user_name"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context).also { INSTANCE = it }
            }
        }
    }
}