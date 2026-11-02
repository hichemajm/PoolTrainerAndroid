package com.pooltrainer

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

data class Ball(val x: Float, val y: Float, val radius: Float, val isCue: Boolean)

object BallDetector {
    fun detect(bitmap: Bitmap): List<Ball> {
        val rgba = Mat(); val gray = Mat(); val circles = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(7.0,7.0),1.5)
            val minDim = minOf(bitmap.width, bitmap.height)
            Imgproc.HoughCircles(gray,circles,Imgproc.HOUGH_GRADIENT,1.2,minDim*0.025,110.0,24.0,(minDim*0.008).toInt().coerceAtLeast(5),(minDim*0.035).toInt().coerceAtLeast(12))
            val found = mutableListOf<Ball>()
            for (i in 0 until circles.cols()) {
                val v = circles.get(0,i) ?: continue
                val x = v[0].toInt().coerceIn(0,bitmap.width-1); val y = v[1].toInt().coerceIn(0,bitmap.height-1)
                val px = rgba.get(y,x) ?: doubleArrayOf(0.0,0.0,0.0,255.0)
                val bright = (px[0]+px[1]+px[2])/3.0
                val cue = bright > 175 && (maxOf(px[0],px[1],px[2])-minOf(px[0],px[1],px[2])) < 65
                found.add(Ball(x.toFloat(),y.toFloat(),v[2].toFloat(),cue))
            }
            found.distinctBy { "${(it.x/8).toInt()}_${(it.y/8).toInt()}" }
        } finally { rgba.release(); gray.release(); circles.release() }
    }
}
