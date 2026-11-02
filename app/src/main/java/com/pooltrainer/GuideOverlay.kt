package com.pooltrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

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
        color = Color.rgb(190, 35, 42)
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
            hypot((it.x-cue.x).toDouble(), (it.y-cue.y).toDouble())
        } ?: return

        current.forEach {
            canvas.drawCircle(it.x, it.y, it.radius, if (it.isCue) cuePaint else ballPaint)
        }
        canvas.drawCircle(target.x, target.y, target.radius + 4f, targetPaint)

        val dx = target.x-cue.x
        val dy = target.y-cue.y
        val length = hypot(dx.toDouble(),dy.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dx/length
        val uy = dy/length
        val ghostX = target.x-ux*(cue.radius+target.radius)
        val ghostY = target.y-uy*(cue.radius+target.radius)
        canvas.drawLine(cue.x,cue.y,ghostX,ghostY,guidePaint)
        canvas.drawCircle(ghostX,ghostY,target.radius*0.72f,targetPaint)

        // Calibrated to the playable cloth, not the phone screen edges.
        val left = width*0.195f
        val right = width*0.805f
        val top = height*0.232f
        val bottom = height*0.915f
        val pockets = listOf(
            left to top, (left+right)/2f to top, right to top,
            left to bottom, (left+right)/2f to bottom, right to bottom
        )
        val pocket = pockets.minByOrNull { p ->
            hypot((p.first-target.x).toDouble(),(p.second-target.y).toDouble())
        } ?: return
        canvas.drawLine(target.x,target.y,pocket.first,pocket.second,targetPaint)
        canvas.drawCircle(pocket.first,pocket.second,10f,pocketPaint)

        // A one-cushion reflected target path is shown as a secondary practice line.
        val bank = reflectedBankPoint(target.x,target.y,pocket.first,pocket.second,left,right,top,bottom)
        if (bank != null) {
            val path = Path().apply {
                moveTo(target.x,target.y)
                lineTo(bank.first,bank.second)
                lineTo(pocket.first,pocket.second)
            }
            val bankPaint = Paint(guidePaint).apply {
                color = Color.rgb(255,185,65)
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(7f,10f),0f)
                strokeWidth = 2.5f
            }
            canvas.drawPath(path,bankPaint)
        }
    }

    private fun reflectedBankPoint(
        sx:Float, sy:Float, px:Float, py:Float,
        left:Float, right:Float, top:Float, bottom:Float
    ): Pair<Float,Float>? {
        // Reflect the pocket across each cushion, then intersect the target-to-image ray.
        val images = listOf(
            (2*left-px) to py, (2*right-px) to py,
            px to (2*top-py), px to (2*bottom-py)
        )
        for (image in images) {
            val vx=image.first-sx
            val vy=image.second-sy
            val candidates=mutableListOf<Pair<Float,Pair<Float,Float>>>()
            if (vx != 0f) {
                val tL=(left-sx)/vx
                val yL=sy+tL*vy
                if(tL>0f && tL<1f && yL in top..bottom) candidates.add(tL to (left to yL))
                val tR=(right-sx)/vx
                val yR=sy+tR*vy
                if(tR>0f && tR<1f && yR in top..bottom) candidates.add(tR to (right to yR))
            }
            if (vy != 0f) {
                val tT=(top-sy)/vy
                val xT=sx+tT*vx
                if(tT>0f && tT<1f && xT in left..right) candidates.add(tT to (xT to top))
                val tB=(bottom-sy)/vy
                val xB=sx+tB*vx
                if(tB>0f && tB<1f && xB in left..right) candidates.add(tB to (xB to bottom))
            }
            val first=candidates.minByOrNull { it.first }?.second
            if(first!=null) return first
        }
        return null
    }
}
