package com.pooltrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.hypot

class GuideOverlay(context: Context) : View(context) {
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 190, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 70, 70)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 190, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 9f), 0f)
    }
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 190, 70)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    @Volatile private var balls: List<Ball> = emptyList()
    @Volatile private var gameActive = false

    fun setFrame(value: List<Ball>, active: Boolean) {
        balls = value
        gameActive = active
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!gameActive) return
        val current = balls
        val cue = current.firstOrNull { it.isCue } ?: return
        val objects = current.filter { !it.isCue }
        val target = objects.minByOrNull {
            hypot((it.x - cue.x).toDouble(), (it.y - cue.y).toDouble())
        } ?: return

        // Draw only after both cue and object balls pass validation.
        current.forEach {
            canvas.drawCircle(it.x, it.y, it.radius, if (it.isCue) cuePaint else ballPaint)
        }
        canvas.drawCircle(target.x, target.y, target.radius + 4f, targetPaint)

        val dx = target.x - cue.x
        val dy = target.y - cue.y
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dx / length
        val uy = dy / length
        val ghostX = target.x - ux * (cue.radius + target.radius)
        val ghostY = target.y - uy * (cue.radius + target.radius)
        canvas.drawLine(cue.x, cue.y, ghostX, ghostY, guidePaint)
        canvas.drawCircle(ghostX, ghostY, target.radius * 0.72f, targetPaint)

        // Approximate route from object ball to the nearest pocket.
        val pockets = listOf(
            0.045f to 0.055f, 0.5f to 0.055f, 0.955f to 0.055f,
            0.045f to 0.945f, 0.5f to 0.945f, 0.955f to 0.945f
        )
        val pocket = pockets.minByOrNull { p ->
            hypot((p.first * width - target.x).toDouble(), (p.second * height - target.y).toDouble())
        }
        if (pocket != null) {
            val px = pocket.first * width
            val py = pocket.second * height
            canvas.drawLine(target.x, target.y, px, py, targetPaint)
            canvas.drawCircle(px, py, 10f, pocketPaint)
        }
    }
}
