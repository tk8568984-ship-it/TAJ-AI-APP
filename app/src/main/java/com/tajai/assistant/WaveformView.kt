package com.tajai.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * A simple animated bar-style waveform (like a voice call/equalizer). Feed it live
 * mic level with pushLevel(rmsDb) — typically from SpeechRecognizer's onRmsChanged,
 * which usually ranges roughly -2..10. When idle, call pushLevel(0f) or just stop
 * calling it and it settles back to a flat line.
 */
class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 24
    private val levels = FloatArray(barCount) { 0.08f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun pushLevel(rmsDb: Float) {
        // Normalize the roughly -2..10 range SpeechRecognizer reports into 0..1
        val normalized = min(1f, max(0.05f, (rmsDb + 2f) / 12f))
        // Shift the history left, add the new sample at the end — classic scrolling look.
        for (i in 0 until barCount - 1) levels[i] = levels[i + 1]
        levels[barCount - 1] = normalized
        invalidate()
    }

    fun reset() {
        for (i in levels.indices) levels[i] = 0.08f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width.toFloat() / (barCount * 1.6f)
        val gap = barWidth * 0.6f
        val midY = height / 2f

        for (i in 0 until barCount) {
            val level = levels[i]
            val barHeight = height * level
            val x = i * (barWidth + gap) + gap
            val top = midY - barHeight / 2f
            val bottom = midY + barHeight / 2f

            // Fade color from emerald (quiet) to cyan/gold (loud) for a lively look.
            paint.color = if (level > 0.6f) {
                android.graphics.Color.rgb(250, 204, 21)
            } else if (level > 0.3f) {
                android.graphics.Color.rgb(6, 182, 212)
            } else {
                android.graphics.Color.rgb(16, 185, 129)
            }
            canvas.drawRoundRect(x, top, x + barWidth, bottom, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
