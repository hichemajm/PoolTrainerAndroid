package com.pooltrainer

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Ball(
    val x: Float,
    val y: Float,
    val radius: Float,
    val isCue: Boolean,
    val confidence: Float
)

object BallDetector {
    // Normalized playing-cloth bounds for the landscape 8 Ball Pool table.
    // HUD, cue power controls, side rails and player panels are excluded.
    private const val TABLE_LEFT = 0.190f
    private const val TABLE_TOP = 0.225f
    private const val TABLE_RIGHT = 0.810f
    private const val TABLE_BOTTOM = 0.925f

    fun detect(bitmap: Bitmap): List<Ball> {
        if (bitmap.width < 320 || bitmap.height < 180) return emptyList()
        val rgba = Mat()
        val gray = Mat()
        val circles = Mat()
        var roi: Mat? = null
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            val left = (bitmap.width * TABLE_LEFT).toInt()
            val top = (bitmap.height * TABLE_TOP).toInt()
            val right = (bitmap.width * TABLE_RIGHT).toInt()
            val bottom = (bitmap.height * TABLE_BOTTOM).toInt()
            roi = rgba.submat(Rect(left, top, (right-left).coerceAtLeast(1), (bottom-top).coerceAtLeast(1)))
            Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.1)

            // Ball diameter is approximately 2.2–2.8% of the short screen dimension.
            val minDim = min(bitmap.width, bitmap.height).toDouble()
            val minRadius = max(7, (minDim * 0.014).toInt())
            val maxRadius = max(minRadius + 2, (minDim * 0.034).toInt())
            Imgproc.HoughCircles(
                gray, circles, Imgproc.HOUGH_GRADIENT, 1.12,
                minDim * 0.030, 100.0, 24.0, minRadius, maxRadius
            )

            val candidates = mutableListOf<Ball>()
            for (i in 0 until circles.cols()) {
                val values = circles.get(0, i) ?: continue
                if (values.size < 3) continue
                val x = values[0].toFloat() + left
                val y = values[1].toFloat() + top
                val radius = values[2].toFloat()
                if (x < bitmap.width * TABLE_LEFT || x > bitmap.width * TABLE_RIGHT) continue
                if (y < bitmap.height * TABLE_TOP || y > bitmap.height * TABLE_BOTTOM) continue
                val sample = sampleBall(rgba, x.toInt(), y.toInt(), radius) ?: continue
                if (sample.confidence < 0.43f) continue
                candidates.add(Ball(x, y, radius, sample.isCue, sample.confidence))
            }

            val selected = mutableListOf<Ball>()
            for (candidate in candidates.sortedByDescending { it.confidence }) {
                val overlaps = selected.any {
                    val dx = it.x - candidate.x
                    val dy = it.y - candidate.y
                    val minDistance = max(it.radius, candidate.radius) * 1.55f
                    dx*dx + dy*dy < minDistance*minDistance
                }
                if (!overlaps) selected.add(candidate)
                if (selected.size >= 16) break
            }

            // The cue ball is the brightest low-saturation ball, not simply the
            // brightest circle anywhere on the screen.
            val cue = selected.filter { it.isCue }.maxByOrNull { it.confidence }
                ?: return emptyList()
            val objects = selected.filter { !it.isCue }
            if (objects.isEmpty()) return emptyList()
            (listOf(cue) + objects).take(16)
        } catch (_: Exception) {
            emptyList()
        } finally {
            roi?.release()
            rgba.release()
            gray.release()
            circles.release()
        }
    }

    private data class Sample(val isCue: Boolean, val confidence: Float)

    private fun sampleBall(mat: Mat, cx: Int, cy: Int, radius: Float): Sample? {
        if (cx < 3 || cy < 3 || cx >= mat.cols()-3 || cy >= mat.rows()-3) return null
        var brightness = 0.0
        var saturation = 0.0
        var count = 0
        // Sample the inner disk, avoiding the specular highlight at the very center.
        for (dy in -3..3 step 2) for (dx in -3..3 step 2) {
            val p = mat.get(cy+dy, cx+dx) ?: continue
            if (p.size < 3) continue
            val hi = max(p[0], max(p[1], p[2]))
            val lo = min(p[0], min(p[1], p[2]))
            brightness += (p[0]+p[1]+p[2])/3.0
            saturation += hi-lo
            count++
        }
        if (count == 0) return null
        brightness /= count
        saturation /= count
        if (brightness < 78.0) return null

        // A ball has a bright interior and a darker boundary. A thin aiming-line
        // ring generally has almost no filled interior and is rejected here.
        var inner = 0.0
        var rim = 0.0
        var outside = 0.0
        var ringSaturation = 0.0
        var ringBrightness = 0.0
        var samples = 0
        for (angle in 0 until 16) {
            val a = angle * Math.PI / 8.0
            fun lum(scale: Double): Double? {
                val x = (cx + cos(a)*radius*scale).toInt()
                val y = (cy + sin(a)*radius*scale).toInt()
                if (x !in 0 until mat.cols() || y !in 0 until mat.rows()) return null
                val px = mat.get(y,x) ?: return null
                return (px[0]+px[1]+px[2])/3.0
            }
            val iv = lum(0.42) ?: continue
            val rv = lum(0.78) ?: continue
            val ov = lum(1.12) ?: continue
            inner += iv
            rim += rv
            outside += ov
            val ringX = (cx + cos(a)*radius*0.58).toInt()
            val ringY = (cy + sin(a)*radius*0.58).toInt()
            val ring = mat.get(ringY, ringX)
            if (ring != null && ring.size >= 3) {
                val hi = max(ring[0], max(ring[1], ring[2]))
                val lo = min(ring[0], min(ring[1], ring[2]))
                ringSaturation += hi-lo
                ringBrightness += (ring[0]+ring[1]+ring[2])/3.0
            }
            samples++
        }
        if (samples < 12) return null
        inner /= samples
        rim /= samples
        outside /= samples
        ringSaturation /= samples
        ringBrightness /= samples
        val fillContrast = inner - outside
        val edgeContrast = rim - outside
        if (fillContrast < 12.0 || edgeContrast < 3.0) return null

        // A stripe ball can have a white number disc at its center. Classify
        // from the wider colored ring instead of the tiny center patch.
        val isCue = brightness > 170.0 && ringBrightness > 150.0 &&
            ringSaturation < 58.0 && saturation < 82.0
        val cueScore = if (isCue) 0.22 else 0.0
        val confidence = ((fillContrast/115.0)*0.48 +
            (edgeContrast/80.0)*0.22 +
            (brightness/255.0)*0.18 +
            (1.0-saturation/255.0)*0.12 + cueScore).toFloat().coerceIn(0f,1f)
        return Sample(isCue, confidence)
    }
}
