package com.smart.msrlab.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// DataStore extension — one instance per process
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_prefs")

/**
 * PreferencesManager — DataStore Preferences wrapper
 *
 * Mirrors Flutter SharedPreferences (gPrefs) usage across all screens:
 *   gPrefs.getString('api_url')            → apiUrlFlow
 *   gPrefs.getBool('adhan_enabled')        → adhanFlow
 *   gPrefs.getBool('vibration_enabled')    → vibrationFlow
 *   gPrefs.getString('calc_method')        → calcMethodFlow
 *   gPrefs.getString('madhab')             → madhabFlow
 *   gPrefs.getInt('tasbeeh_count')         → tasbeehCountFlow
 *   gPrefs.getInt('tasbeeh_target')        → tasbeehTargetFlow
 */
class PreferencesManager(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    // ── Key definitions — exact same key names as Flutter ─────────────────────
    companion object {
        val KEY_API_URL         = stringPreferencesKey("api_url")
        val KEY_ADHAN_ENABLED   = booleanPreferencesKey("adhan_enabled")
        val KEY_VIBRATION       = booleanPreferencesKey("vibration_enabled")
        val KEY_CALC_METHOD     = stringPreferencesKey("calc_method")
        val KEY_MADHAB          = stringPreferencesKey("madhab")
        val KEY_TASBEEH_COUNT   = intPreferencesKey("tasbeeh_count")
        val KEY_TASBEEH_TARGET  = intPreferencesKey("tasbeeh_target")

        const val DEFAULT_API_URL = "https://halal-app.smartnode.my"
        const val DEFAULT_CALC    = "Singapore"
        const val DEFAULT_MADHAB  = "Shafi"
    }

    // ── Reactive Flows — collect in Compose with collectAsStateWithLifecycle() ─
    val apiUrlFlow: Flow<String>     = dataStore.data.map { it[KEY_API_URL]       ?: DEFAULT_API_URL }
    val adhanFlow: Flow<Boolean>     = dataStore.data.map { it[KEY_ADHAN_ENABLED] ?: true }
    val vibrationFlow: Flow<Boolean> = dataStore.data.map { it[KEY_VIBRATION]     ?: true }
    val calcMethodFlow: Flow<String> = dataStore.data.map { it[KEY_CALC_METHOD]   ?: DEFAULT_CALC }
    val madhabFlow: Flow<String>     = dataStore.data.map { it[KEY_MADHAB]        ?: DEFAULT_MADHAB }
    val tasbeehCountFlow: Flow<Int>  = dataStore.data.map { it[KEY_TASBEEH_COUNT] ?: 0 }
    val tasbeehTargetFlow: Flow<Int> = dataStore.data.map { it[KEY_TASBEEH_TARGET] ?: 33 }

    // ── Blocking reads — only for ViewModel.init{} or non-UI coroutines ────────
    fun getApiUrlBlocking(): String     = runBlocking { dataStore.data.first()[KEY_API_URL]       ?: DEFAULT_API_URL }
    fun getAdhanBlocking(): Boolean     = runBlocking { dataStore.data.first()[KEY_ADHAN_ENABLED] ?: true }
    fun getTasbeehCountBlocking(): Int  = runBlocking { dataStore.data.first()[KEY_TASBEEH_COUNT] ?: 0 }
    fun getTasbeehTargetBlocking(): Int = runBlocking { dataStore.data.first()[KEY_TASBEEH_TARGET] ?: 33 }

    // ── Suspend writers — call from coroutine scope ────────────────────────────
    suspend fun setApiUrl(url: String) = dataStore.edit {
        if (url.isBlank()) it.remove(KEY_API_URL) else it[KEY_API_URL] = url.trim()
    }
    suspend fun setAdhan(enabled: Boolean)    = dataStore.edit { it[KEY_ADHAN_ENABLED] = enabled }
    suspend fun setVibration(enabled: Boolean) = dataStore.edit { it[KEY_VIBRATION]    = enabled }
    suspend fun setCalcMethod(method: String) = dataStore.edit { it[KEY_CALC_METHOD]   = method }
    suspend fun setMadhab(madhab: String)     = dataStore.edit { it[KEY_MADHAB]        = madhab }
    suspend fun setTasbeehCount(count: Int)   = dataStore.edit { it[KEY_TASBEEH_COUNT] = count }
    suspend fun setTasbeehTarget(target: Int) = dataStore.edit { it[KEY_TASBEEH_TARGET] = target }
}
