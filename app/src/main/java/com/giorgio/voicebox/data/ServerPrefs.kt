package com.giorgio.voicebox.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object ServerPrefs {
    private val KEY_BASE_URL = stringPreferencesKey("base_url")
    private val KEY_LAST_MODEL = stringPreferencesKey("last_model")

    fun baseUrlFlow(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_BASE_URL] ?: "" }

    suspend fun baseUrl(context: Context): String =
        baseUrlFlow(context).first()

    suspend fun saveBaseUrl(context: Context, url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = ApiClient.normalizeBaseUrl(url) }
    }

    suspend fun lastModel(context: Context): String =
        context.dataStore.data.map { it[KEY_LAST_MODEL] ?: "" }.first()

    suspend fun saveLastModel(context: Context, modelName: String) {
        context.dataStore.edit { it[KEY_LAST_MODEL] = modelName }
    }
}
