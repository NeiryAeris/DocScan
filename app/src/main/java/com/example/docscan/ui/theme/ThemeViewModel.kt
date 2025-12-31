package com.example.docscan.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemeViewModel(private val context: Context) : ViewModel() {

    private val themeKey = stringPreferencesKey("theme")

    // Default to Light theme
    private val _theme = MutableStateFlow(Theme.LIGHT)
    val theme: StateFlow<Theme> = _theme

    init {
        viewModelScope.launch {
            context.dataStore.data
                .map { preferences ->
                    val themeName = preferences[themeKey] ?: Theme.LIGHT.name
                    try {
                        Theme.valueOf(themeName)
                    } catch (e: IllegalArgumentException) {
                        Theme.LIGHT
                    }
                }
                .collect { _theme.value = it }
        }
    }

    fun setTheme(theme: Theme) {
        viewModelScope.launch {
            _theme.value = theme
            context.dataStore.edit {
                it[themeKey] = theme.name
            }
        }
    }
}

enum class Theme {
    LIGHT,
    DARK
}
