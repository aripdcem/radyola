package com.aripd.radyola.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "radyola_settings")

/** Kalıcı ayarlar: son istasyon, otomatik başlatma ve favoriler. */
class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            rememberStation = prefs[KEY_REMEMBER] ?: true,
            autoplayOnStart = prefs[KEY_AUTOPLAY] ?: false,
            lastStationId = prefs[KEY_LAST_STATION] ?: "",
            favorites = prefs[KEY_FAVORITES] ?: emptySet()
        )
    }

    suspend fun setRememberStation(value: Boolean) = context.dataStore.edit { prefs ->
        prefs[KEY_REMEMBER] = value
        if (!value) prefs.remove(KEY_LAST_STATION)
    }

    suspend fun setAutoplayOnStart(value: Boolean) = context.dataStore.edit { prefs ->
        prefs[KEY_AUTOPLAY] = value
    }

    suspend fun setLastStation(id: String) = context.dataStore.edit { prefs ->
        if (prefs[KEY_REMEMBER] != false) prefs[KEY_LAST_STATION] = id
    }

    /**
     * Eski favori kümesini siler.
     *
     * Favoriler artık ayrı tutulmuyor — kullanıcı listesi zaten seçtikleri.
     * Küme yalnız bir kez, taşıma sırasında okunuyor.
     */
    suspend fun clearFavorites() = context.dataStore.edit { prefs ->
        prefs.remove(KEY_FAVORITES)
    }

    private companion object {
        val KEY_REMEMBER = booleanPreferencesKey("remember_station")
        val KEY_AUTOPLAY = booleanPreferencesKey("autoplay_on_start")
        val KEY_LAST_STATION = stringPreferencesKey("last_station")
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
    }
}

data class AppSettings(
    val rememberStation: Boolean = true,
    val autoplayOnStart: Boolean = false,
    val lastStationId: String = "",
    /** Yalnız eski sürümden taşıma için okunur; [SettingsStore.clearFavorites] siler. */
    val favorites: Set<String> = emptySet()
)
