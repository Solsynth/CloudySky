package dev.solsynth.cloudysky.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.openid.appauth.AuthState

class AuthStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): AuthState = prefs.getString(KEY_AUTH_STATE, null)?.let { saved ->
        AuthState.jsonDeserialize(saved)
    } ?: AuthState()

    fun save(state: AuthState) {
        prefs.edit().putString(KEY_AUTH_STATE, state.jsonSerializeString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_AUTH_STATE).apply()
    }

    private companion object {
        const val FILE_NAME = "auth_state"
        const val KEY_AUTH_STATE = "auth_state_json"
    }
}
