package com.pooltrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.hypot

class GuideOverlay(context: Context) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.RED; style=Paint.Style.STROKE; strokeWidth=3f }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; style=Paint.Style.STROKE; strokeWidth=4f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=26f; setShadowLayer(5f,1f,1f,Color.BLACK) }
    @Volatile private var balls: List<Ball> = emptyList()
    fun setDetections(value: List<Ball>) { balls=value; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val current=balls
        current.forEach { canvas.drawCircle(it.x,it.y,it.radius,ring) }
        val cue=current.firstOrNull { it.isCue }
        if (cue != null) {
            val target=current.filter { !it.isCue }.minByOrNull { hypot((it.x-cue.x).toDouble(),(it.y-cue.y).toDouble()) }
            if (target != null) { canvas.drawLine(cue.x,cue.y,target.x,target.y,guide); canvas.drawText("Detected: ${current.size} circles",24f,52f,label) }
        } else canvas.drawText("Searching for balls...",24f,52f,label)
    }
}
