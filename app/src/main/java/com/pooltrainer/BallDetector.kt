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
    fun detect(bitmap: Bitmap): List<Ball> {
        if (bitmap.width < 320 || bitmap.height < 180) return emptyList()
        val rgba = Mat()
        val gray = Mat()
        val circles = Mat()
        var roi: Mat? = null
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            // Exclude most side rails, menu controls and status elements.
            val left = (bitmap.width * 0.045f).toInt()
            val top = (bitmap.height * 0.055f).toInt()
            val right = (bitmap.width * 0.955f).toInt()
            val bottom = (bitmap.height * 0.945f).toInt()
            roi = rgba.submat(Rect(left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1)))
            Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.15)

            val minDim = min(bitmap.width, bitmap.height).toDouble()
            val minRadius = max(7, (minDim * 0.0105).toInt())
            val maxRadius = max(minRadius + 2, (minDim * 0.030).toInt())
            Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.15,
                minDim * 0.026, 105.0, 27.0, minRadius, maxRadius)

            val candidates = mutableListOf<Ball>()
            for (i in 0 until circles.cols()) {
                val values = circles.get(0, i) ?: continue
                if (values.size < 3) continue
                val x = values[0].toFloat() + left
                val y = values[1].toFloat() + top
                val radius = values[2].toFloat()
                if (x < bitmap.width * 0.075f || x > bitmap.width * 0.925f) continue
                if (y < bitmap.height * 0.09f || y > bitmap.height * 0.91f) continue
                val sample = sampleBall(rgba, x.toInt(), y.toInt(), radius) ?: continue
                if (sample.confidence < 0.34f) continue
                candidates.add(Ball(x, y, radius, sample.isCue, sample.confidence))
            }

            // Non-maximum suppression: nearby detections are usually one ball.
            val selected = mutableListOf<Ball>()
            for (candidate in candidates.sortedByDescending { it.confidence }) {
                val overlaps = selected.any {
                    val dx = it.x - candidate.x
                    val dy = it.y - candidate.y
                    val minDistance = max(it.radius, candidate.radius) * 1.45f
                    dx * dx + dy * dy < minDistance * minDistance
                }
                if (!overlaps) selected.add(candidate)
                if (selected.size >= 16) break
            }

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
        if (cx < 2 || cy < 2 || cx >= mat.cols() - 2 || cy >= mat.rows() - 2) return null
        var centerBrightness = 0.0
        var centerSaturation = 0.0
        var centerCount = 0
        for (dy in -2..2 step 2) for (dx in -2..2 step 2) {
            val p = mat.get(cy + dy, cx + dx) ?: continue
            if (p.size < 3) continue
            val hi = max(p[0], max(p[1], p[2]))
            val lo = min(p[0], min(p[1], p[2]))
            centerBrightness += (p[0] + p[1] + p[2]) / 3.0
            centerSaturation += hi - lo
            centerCount++
        }
        if (centerCount == 0) return null
        centerBrightness /= centerCount
        centerSaturation /= centerCount
        if (centerBrightness < 72.0) return null

        // A real ball normally has a bright rim against the felt/background.
        var inner = 0.0
        var outer = 0.0
        var count = 0
        for (angle in 0 until 12) {
            val a = angle * Math.PI / 6.0
            val ix = (cx + cos(a) * radius * 0.58).toInt()
            val iy = (cy + sin(a) * radius * 0.58).toInt()
            val ox = (cx + cos(a) * radius * 1.02).toInt()
            val oy = (cy + sin(a) * radius * 1.02).toInt()
            if (ix !in 0 until mat.cols() || iy !in 0 until mat.rows()) continue
            if (ox !in 0 until mat.cols() || oy !in 0 until mat.rows()) continue
            val pi = mat.get(iy, ix) ?: continue
            val po = mat.get(oy, ox) ?: continue
            inner += (pi[0] + pi[1] + pi[2]) / 3.0
            outer += (po[0] + po[1] + po[2]) / 3.0
            count++
        }
        if (count < 8) return null
        inner /= count
        outer /= count
        val rimContrast = (inner - outer).coerceAtLeast(0.0)
        val confidence = ((rimContrast / 70.0) * 0.62 +
            (centerBrightness / 255.0) * 0.28 +
            (1.0 - centerSaturation / 255.0) * 0.10).toFloat().coerceIn(0f, 1f)
        val cue = centerBrightness > 185.0 && centerSaturation < 48.0 && rimContrast > 8.0
        return Sample(cue, confidence)
    }
}
