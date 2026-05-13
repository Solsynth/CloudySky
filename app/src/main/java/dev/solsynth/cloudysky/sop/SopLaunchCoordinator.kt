package dev.solsynth.cloudysky.sop

import android.content.Context

class SopLaunchCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val repository = SopRepository(appContext)

    fun requestStart() {
        repository.requestStart()
    }

    fun startIfPending() {
        val state = repository.currentState()
        if (!state.enabled) return
        if (!repository.consumePendingStart()) return
        SopListenerService.start(appContext)
    }

    fun stop() {
        repository.setEnabled(false)
        SopListenerService.stop(appContext)
    }
}
