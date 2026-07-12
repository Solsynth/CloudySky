package dev.solsynth.cloudysky.auth

import android.net.Uri
import dev.solsynth.cloudysky.BuildConfig
import net.openid.appauth.AuthorizationServiceConfiguration

object AuthConfig {
    val clientId: String = BuildConfig.OIDC_CLIENT_ID

    val redirectUri: Uri = Uri.parse("cloudysky://oauth2redirect/callback")

    val serviceConfiguration = AuthorizationServiceConfiguration(
        Uri.parse("https://id.solian.app/auth/authorize"),
        Uri.parse("https://api.solian.app/padlock/auth/open/token")
    )

    const val scopeOpenId = "openid"
    const val scopeProfile = "profile"
    const val scopeEmail = "email"
    const val scopeChatMessagesCreate = "chat.messages.create"
    const val scopeNotificationsSopSubscribe = "notifications.sop.subscribe"
}
