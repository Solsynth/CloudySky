package dev.solsynth.cloudysky.sop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.solsynth.cloudysky.auth.AuthRepository

class SopBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val authRepository = AuthRepository(context.applicationContext)
        val sopRepository = SopRepository(context.applicationContext)
        if (authRepository.authState.value.isAuthorized && sopRepository.currentState().enabled) {
            SopLaunchCoordinator(context.applicationContext).requestStart()
        }
    }
}
