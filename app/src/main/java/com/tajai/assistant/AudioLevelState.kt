package com.tajai.assistant

import android.util.Log

/**
 * Carries live microphone volume (from SpeechRecognizer's onRmsChanged) from
 * FloatingOrbService to whatever screen wants to draw a waveform. Same
 * same-process observer pattern as AgentState.
 */
object AudioLevelState {
    private const val TAG = "AudioLevelState"
    private val listeners = mutableListOf<(Float) -> Unit>()

    fun push(rmsDb: Float) {
        listeners.toList().forEach {
            try {
                it(rmsDb)
            } catch (e: Throwable) {
                Log.e(TAG, "Waveform observer failed", e)
            }
        }
    }

    fun observe(listener: (Float) -> Unit) {
        listeners.add(listener)
    }

    fun removeObserver(listener: (Float) -> Unit) {
        listeners.remove(listener)
    }
}
