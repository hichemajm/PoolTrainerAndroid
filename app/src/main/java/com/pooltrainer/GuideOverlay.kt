package com.pooltrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val statusBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 12, 12, 15)
        style = Paint.Style.FILL
    }
    private val statusText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * resources.displayMetrics.density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }
    private val statusAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 35, 42)
        style = Paint.Style.FILL
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
        val density = resources.displayMetrics.density
        val current = balls
        drawStatus(canvas, density, current.size)

        if (!gameActive || !GuidePrefs.enabled(context, GuidePrefs.AI_GUIDE)) return
        val cue = current.firstOrNull { it.isCue } ?: return
        val objects = current.filter { !it.isCue }
        val target = objects.minByOrNull {
            hypot((it.x-cue.x).toDouble(), (it.y-cue.y).toDouble())
        } ?: return

        if (GuidePrefs.enabled(context, GuidePrefs.LINE_GUIDES)) {
            current.forEach {
                canvas.drawCircle(it.x, it.y, it.radius, if (it.isCue) cuePaint else ballPaint)
            }
            canvas.drawCircle(target.x, target.y, target.radius + 4f, targetPaint)
        }

        val dx = target.x-cue.x
        val dy = target.y-cue.y
        val length = hypot(dx.toDouble(),dy.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dx/length
        val uy = dy/length
        val ghostX = target.x-ux*(cue.radius+target.radius)
        val ghostY = target.y-uy*(cue.radius+target.radius)
        if (GuidePrefs.enabled(context, GuidePrefs.LINE_GUIDES)) {
            canvas.drawLine(cue.x,cue.y,ghostX,ghostY,guidePaint)
            canvas.drawCircle(ghostX,ghostY,target.radius*0.72f,targetPaint)
        }
        if (GuidePrefs.enabled(context, GuidePrefs.EXTENDED_LINES)) {
            val edge = rayToTableEdge(ghostX, ghostY, ux, uy,
                width*0.195f, width*0.805f, height*0.232f, height*0.915f)
            if (edge != null) {
                val extendedPaint = Paint(guidePaint).apply {
                    color = Color.WHITE
                    pathEffect = null
                    strokeWidth = 2.2f
                }
                canvas.drawLine(ghostX, ghostY, edge.first, edge.second, extendedPaint)
            }
        }

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

        val bank = if (GuidePrefs.enabled(context, GuidePrefs.BANK_SHOTS))
            reflectedBankPoint(target.x,target.y,pocket.first,pocket.second,left,right,top,bottom) else null
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

    private fun drawStatus(canvas: Canvas, density: Float, count: Int) {
        val x = 12f * density
        val y = 12f * density
        val h = 34f * density
        val label = when {
            !gameActive -> "AIM TOOL  •  WAITING FOR 8 BALL POOL"
            count == 0 -> "AIM TOOL  •  GAME FOUND / SCANNING TABLE"
            else -> "AIM TOOL  •  $count BALLS DETECTED"
        }
        statusText.textSize = 11f * density
        val w = statusText.measureText(label) + 30f * density
        canvas.drawRoundRect(RectF(x, y, x+w, y+h), 8f*density, 8f*density, statusBg)
        canvas.drawCircle(x+12f*density, y+h/2f, 4f*density, statusAccent)
        canvas.drawText(label, x+23f*density, y+h/2f+4f*density, statusText)
    }

    private fun rayToTableEdge(
        sx: Float, sy: Float, dx: Float, dy: Float,
        left: Float, right: Float, top: Float, bottom: Float
    ): Pair<Float,Float>? {
        val hits = mutableListOf<Pair<Float,Pair<Float,Float>>>()
        if (dx > 0f) {
            val t=(right-sx)/dx; val y=sy+t*dy
            if (t>0f && y in top..bottom) hits.add(t to (right to y))
        } else if (dx < 0f) {
            val t=(left-sx)/dx; val y=sy+t*dy
            if (t>0f && y in top..bottom) hits.add(t to (left to y))
        }
        if (dy > 0f) {
            val t=(bottom-sy)/dy; val x=sx+t*dx
            if (t>0f && x in left..right) hits.add(t to (x to bottom))
        } else if (dy < 0f) {
            val t=(top-sy)/dy; val x=sx+t*dx
            if (t>0f && x in left..right) hits.add(t to (x to top))
        }
        return hits.minByOrNull { it.first }?.second
    }

    private fun reflectedBankPoint(
        sx:Float, sy:Float, px:Float, py:Float,
        left:Float, right:Float, top:Float, bottom:Float
    ): Pair<Float,Float>? {
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
