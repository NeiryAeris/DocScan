package com.example.docscan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(context: Context) {

    private object PreferencesKeys {
        val IS_BACKUP_ENABLED = booleanPreferencesKey("is_backup_enabled")
    }

    private val dataStore = context.dataStore

    val isBackupEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.IS_BACKUP_ENABLED] ?: false
        }

    suspend fun setBackupEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_BACKUP_ENABLED] = isEnabled
        }
    }
}
