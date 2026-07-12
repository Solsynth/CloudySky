package dev.solsynth.cloudysky.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.solsynth.cloudysky.BuildConfig
import net.openid.appauth.AuthorizationServiceConfiguration

object AuthConfig {
    private const val solianPackageName = "dev.solsynth.solian"
    private val webAuthorizationEndpoint = Uri.parse("https://id.solian.app/auth/authorize")
    private val solianAuthorizationEndpoint = Uri.parse("solian://auth/authorize")

    val clientId: String = BuildConfig.OIDC_CLIENT_ID
    val clientSecret: String = BuildConfig.OIDC_CLIENT_SECRET

    val redirectUri: Uri = Uri.parse("cloudysky://oauth2redirect/callback")

    fun serviceConfiguration(context: Context): AuthorizationServiceConfiguration {
        val packageManager = context.packageManager
        val solianInstalled = try {
            packageManager.getApplicationInfo(solianPackageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val protocolSupported = Intent(Intent.ACTION_VIEW, solianAuthorizationEndpoint)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .resolveActivity(packageManager) != null

        return AuthorizationServiceConfiguration(
            if (solianInstalled || protocolSupported) solianAuthorizationEndpoint else webAuthorizationEndpoint,
            Uri.parse("https://api.solian.app/padlock/auth/open/token")
        )
    }

    const val scopeOpenId = "openid"
    const val scopeProfile = "profile"
    const val scopeEmail = "email"
    const val scopeChatMessagesCreate = "chat.messages.create"
}
