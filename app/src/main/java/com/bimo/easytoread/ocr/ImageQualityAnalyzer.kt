package com.bimo.easytoread.ocr

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

internal object ImageQualityAnalyzer {
    data class Report(
        val score: Float,
        val warnings: List<String>,
    ) {
        fun traceValue(): String = if (warnings.isEmpty()) "pass" else warnings.joinToString(",")
    }

    fun analyze(bitmap: Bitmap): Report {
        val warnings = mutableListOf<String>()
        if (minOf(bitmap.width, bitmap.height) < 1000) warnings += "low-resolution"

        val rgba = Mat()
        val gray = Mat()
        val laplacian = Mat()
        val mean = MatOfDouble()
        val deviation = MatOfDouble()
        val glare = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, deviation)
            val blurVariance = deviation.toArray().firstOrNull()?.let { it * it } ?: 0.0
            if (blurVariance < 70.0) warnings += "blur"

            Core.meanStdDev(gray, mean, deviation)
            val contrast = deviation.toArray().firstOrNull() ?: 0.0
            if (contrast < 28.0) warnings += "low-contrast"

            Core.inRange(gray, Scalar(248.0), Scalar(255.0), glare)
            val glareRatio = Core.countNonZero(glare).toDouble() /
                    maxOf(1.0, gray.rows().toDouble() * gray.cols().toDouble())
            if (glareRatio > 0.22) warnings += "glare"

            val penalty = warnings.size * 0.18f
            Report((1.0f - penalty).coerceIn(0f, 1f), warnings)
        } finally {
            rgba.release()
            gray.release()
            laplacian.release()
            mean.release()
            deviation.release()
            glare.release()
        }
    }
}
