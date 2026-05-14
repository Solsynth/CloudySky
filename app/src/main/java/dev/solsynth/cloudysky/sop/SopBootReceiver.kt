package dev.solsynth.cloudysky.sop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.solsynth.cloudysky.auth.AuthRepository

class SopBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val authRepository = AuthRepository(appContext)
        val sopRepository = SopRepository(appContext)
        val state = sopRepository.currentState()
        if (authRepository.authState.value.isAuthorized && state.enabled && state.autoStartOnBoot) {
            SopListenerService.start(appContext)
        }
    }
}
