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

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BG_PLAY, enabled).apply()
        _backgroundPlayEnabled.value = enabled
    }

    fun isBackgroundPlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_BG_PLAY, true)
    }

    companion object {
        private const val KEY_BG_PLAY = "background_play_enabled"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context).also { INSTANCE = it }
            }
        }
    }
}