package dev.solsynth.cloudysky.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthState.AuthStateAction
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import dev.solsynth.cloudysky.api.AccountsApi
import dev.solsynth.cloudysky.sop.SopStore

class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val authService = AuthorizationService(appContext)
    private val store = AuthStateStore(appContext)
    private val sopStore = SopStore(appContext)
    private val accountsApi = AccountsApi()
    private val clientAuthentication: ClientAuthentication? =
        AuthConfig.clientSecret.takeIf { it.isNotBlank() }?.let { ClientSecretPost(it) }

    private val _authState = MutableStateFlow(store.load())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun createAuthorizationIntent(): Intent {
        Log.d(TAG, "createAuthorizationIntent: clientId=${AuthConfig.clientId}, redirectUri=${AuthConfig.redirectUri}")
        if (clientAuthentication == null) {
            Log.w(TAG, "client secret is empty; token exchange will fail until cloudyskyClientSecret is provided")
        }
        val request = AuthorizationRequest.Builder(
            AuthConfig.serviceConfiguration,
            AuthConfig.clientId,
            ResponseTypeValues.CODE,
            AuthConfig.redirectUri
        )
            .setScopes(
                AuthConfig.scopeOpenId,
                AuthConfig.scopeProfile,
                AuthConfig.scopeEmail,
                AuthConfig.scopeChatMessagesCreate
            )
            .build()

        return authService.getAuthorizationRequestIntent(request)
    }

    fun handleAuthorizationResult(data: Intent?) {
        if (data == null) return

        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        Log.d(TAG, "handleAuthorizationResult: response=${response != null}, exception=${exception?.errorDescription ?: exception?.error}")
        if (response == null && exception == null) return

        val updated = _authState.value
        updated.update(response, exception)
        val persisted = freshState(updated)
        store.save(persisted)
        _authState.value = persisted
        Log.d(TAG, "handleAuthorizationResult: authorized=${persisted.isAuthorized}, needsTokenRefresh=${persisted.needsTokenRefresh}")

        if (response != null && exception == null) {
            exchangeAuthorizationCode(response)
        }
    }

    fun signOut() {
        Log.d(TAG, "signOut")
        _authState.value = AuthState()
        store.clear()
        sopStore.clearAll()
    }

    suspend fun accessToken(): String? = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "accessToken: requesting fresh token")
        val state = _authState.value
        val callback = object : AuthStateAction {
            override fun execute(accessToken: String?, idToken: String?, exception: AuthorizationException?) {
                if (exception != null) {
                    Log.e(TAG, "accessToken: refresh failed", exception)
                    if (continuation.isActive) continuation.resume(null)
                } else if (continuation.isActive) {
                    Log.d(TAG, "accessToken: success tokenPresent=${accessToken != null}")
                    store.save(_authState.value)
                    continuation.resume(accessToken)
                }
            }
        }

        if (clientAuthentication != null) {
            state.performActionWithFreshTokens(authService, clientAuthentication, callback)
        } else {
            state.performActionWithFreshTokens(authService, callback)
        }
    }

    suspend fun fetchCurrentAccount(): CurrentAccount? {
        val token = accessToken() ?: return null
        Log.d(TAG, "fetchCurrentAccount: token available, fetching profile")
        return withContext(Dispatchers.IO) { accountsApi.getCurrentAccount(token) }
    }

    private fun exchangeAuthorizationCode(response: AuthorizationResponse) {
        Log.d(TAG, "exchangeAuthorizationCode: code received, exchanging")
        val request = response.createTokenExchangeRequest()
        val callback = object : AuthorizationService.TokenResponseCallback {
            override fun onTokenRequestCompleted(
                tokenResponse: net.openid.appauth.TokenResponse?,
                exception: AuthorizationException?
            ) {
                val updated = _authState.value
                updated.update(tokenResponse, exception)
                val persisted = freshState(updated)
                store.save(persisted)
                _authState.value = persisted
                if (exception != null) {
                    Log.e(TAG, "exchangeAuthorizationCode: failed", exception)
                } else {
                    Log.d(TAG, "exchangeAuthorizationCode: success authorized=${persisted.isAuthorized}")
                }
            }
        }

        if (clientAuthentication != null) {
            authService.performTokenRequest(request, clientAuthentication, callback)
        } else {
            authService.performTokenRequest(request, callback)
        }
    }

    private companion object {
        const val TAG = "CloudySkyAuth"
    }

    private fun freshState(state: AuthState): AuthState {
        return AuthState.jsonDeserialize(state.jsonSerializeString())
    }
}
