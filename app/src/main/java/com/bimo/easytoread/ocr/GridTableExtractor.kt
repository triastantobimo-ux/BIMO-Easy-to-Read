package com.bimo.easytoread.ocr

import android.graphics.Bitmap
import android.graphics.Color
import com.bimo.easytoread.core.WorksheetModel
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.OCRResult
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

internal object GridTableExtractor {
    private const val MAX_REFINEMENT_CELLS = 20

    private data class Grid(
        val vertical: List<Int>,
        val horizontal: List<Int>,
    )

    suspend fun extract(
        bitmap: Bitmap,
        paddle: PaddleOCR,
        pageResults: List<OCRResult>,
    ): WorksheetModel? {
        val grid = detectGrid(bitmap) ?: return null
        val rows = grid.horizontal.size - 1
        val columns = grid.vertical.size - 1
        if (rows < 2 || columns < 2 || rows * columns > 500) return null

        val cells = mutableListOf<WorksheetModel.Cell>()
        var confidenceTotal = 0.0
        var confidenceCount = 0
        var lowConfidence = 0
        var refinements = 0

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val left = grid.vertical[column]
                val right = grid.vertical[column + 1]
                val top = grid.horizontal[row]
                val bottom = grid.horizontal[row + 1]
                if (right - left < 8 || bottom - top < 8) continue

                val assigned = pageResults.filter { result ->
                    val bounds = bounds(result)
                    val centerX = (bounds[0] + bounds[2]) / 2f
                    val centerY = (bounds[1] + bounds[3]) / 2f
                    centerX > left && centerX < right && centerY > top && centerY < bottom
                }.sortedWith(compareBy<OCRResult>({ bounds(it)[1] }, { bounds(it)[0] }))

                var text = assigned.joinToString(" ") { it.text.trim() }.trim()
                var confidence = assigned.map { it.confidence }.filter { it >= 0f }.averageOrNull()
                    ?: -1.0

                val shouldRefine = refinements < MAX_REFINEMENT_CELLS &&
                        inkDensity(bitmap, left, top, right, bottom) >= 0.025 &&
                        (text.isEmpty() || confidence < 0.80)
                if (shouldRefine) {
                    refinements++
                    val inset = max(2, min(right - left, bottom - top) / 40)
                    val cropLeft = min(right - 1, left + inset)
                    val cropTop = min(bottom - 1, top + inset)
                    val cropRight = max(cropLeft + 1, right - inset)
                    val cropBottom = max(cropTop + 1, bottom - inset)
                    val crop = Bitmap.createBitmap(
                        bitmap,
                        cropLeft,
                        cropTop,
                        cropRight - cropLeft,
                        cropBottom - cropTop,
                    )
                    try {
                        val refined = paddle.recognize(crop).results
                        val refinedText = refined.joinToString(" ") { it.text.trim() }.trim()
                        val refinedConfidence = refined.map { it.confidence }
                            .filter { it >= 0f }.averageOrNull() ?: -1.0
                        if (refinedText.isNotEmpty() &&
                            (text.isEmpty() || refinedConfidence >= confidence - 0.03)) {
                            text = refinedText
                            confidence = refinedConfidence
                        }
                    } finally {
                        crop.recycle()
                    }
                }

                val safeConfidence = confidence.toFloat().coerceIn(-1f, 1f)
                if (text.isNotEmpty()) {
                    if (safeConfidence >= 0f) {
                        confidenceTotal += safeConfidence
                        confidenceCount++
                    }
                    if (safeConfidence < 0.88f) lowConfidence++
                }
                cells += WorksheetModel.Cell(
                    row,
                    column,
                    1,
                    1,
                    text,
                    safeConfidence,
                )
            }
        }

        val textConfidence = if (confidenceCount == 0) 0f
            else (confidenceTotal / confidenceCount).toFloat()
        val topologyConfidence = (
                0.78f + min(0.18f, (rows + columns) * 0.01f)
        ).coerceAtMost(0.98f)
        val status = if (lowConfidence == 0 && textConfidence >= 0.94f)
            WorksheetModel.VerificationStatus.AUTOMATIC
        else WorksheetModel.VerificationStatus.REVIEW_REQUIRED

        return WorksheetModel(
            rows,
            columns,
            cells,
            selectHeaderRow(cells, rows, columns),
            topologyConfidence,
            textConfidence,
            status,
            "wired-grid projection + PP-OCRv6 Medium cell refinement; refinements=" + refinements,
        )
    }

    private fun detectGrid(bitmap: Bitmap): Grid? {
        val rgba = Mat()
        val gray = Mat()
        val binary = Mat()
        val vertical = Mat()
        val horizontal = Mat()
        val verticalProjection = Mat()
        val horizontalProjection = Mat()
        val verticalKernel = Mat()
        val horizontalKernel = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.adaptiveThreshold(
                gray,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                31,
                12.0,
            )

            val vKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(1.0, max(16, gray.rows() / 28).toDouble()),
            )
            val hKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(max(16, gray.cols() / 28).toDouble(), 1.0),
            )
            vKernel.copyTo(verticalKernel)
            hKernel.copyTo(horizontalKernel)
            vKernel.release()
            hKernel.release()

            Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, verticalKernel)
            Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, horizontalKernel)
            Core.reduce(vertical, verticalProjection, 0, Core.REDUCE_SUM, CvType.CV_32S)
            Core.reduce(horizontal, horizontalProjection, 1, Core.REDUCE_SUM, CvType.CV_32S)

            val xValues = IntArray(gray.cols())
            val yValues = IntArray(gray.rows())
            verticalProjection.get(0, 0, xValues)
            horizontalProjection.get(0, 0, yValues)
            val xs = clusterPositions(xValues, (gray.rows() * 255 * 0.18).toInt())
            val ys = clusterPositions(yValues, (gray.cols() * 255 * 0.18).toInt())
            if (xs.size < 3 || ys.size < 3) null else Grid(xs, ys)
        } finally {
            rgba.release()
            gray.release()
            binary.release()
            vertical.release()
            horizontal.release()
            verticalProjection.release()
            horizontalProjection.release()
            verticalKernel.release()
            horizontalKernel.release()
        }
    }

    private fun clusterPositions(values: IntArray, threshold: Int): List<Int> {
        val output = mutableListOf<Int>()
        var start = -1
        for (index in values.indices) {
            if (values[index] >= threshold && start < 0) start = index
            val ends = start >= 0 && (values[index] < threshold || index == values.lastIndex)
            if (ends) {
                val end = if (values[index] < threshold) index - 1 else index
                output += (start + end) / 2
                start = -1
            }
        }
        if (output.size < 2) return output
        val filtered = mutableListOf(output.first())
        for (position in output.drop(1)) {
            if (position - filtered.last() >= 8) filtered += position
        }
        return filtered
    }

    private fun bounds(result: OCRResult): FloatArray {
        val xs = result.box.points.map { it.x }
        val ys = result.box.points.map { it.y }
        return floatArrayOf(
            xs.minOrNull() ?: 0f,
            ys.minOrNull() ?: 0f,
            xs.maxOrNull() ?: 0f,
            ys.maxOrNull() ?: 0f,
        )
    }

    private fun inkDensity(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Double {
        val inset = 3
        val l = (left + inset).coerceIn(0, bitmap.width - 1)
        val t = (top + inset).coerceIn(0, bitmap.height - 1)
        val r = (right - inset).coerceIn(l + 1, bitmap.width)
        val b = (bottom - inset).coerceIn(t + 1, bitmap.height)
        val step = max(1, min(r - l, b - t) / 48)
        var dark = 0
        var samples = 0
        var y = t
        while (y < b) {
            var x = l
            while (x < r) {
                if (Color.luminance(bitmap.getPixel(x, y)) < 0.72f) dark++
                samples++
                x += step
            }
            y += step
        }
        return if (samples == 0) 0.0 else dark.toDouble() / samples
    }

    private fun selectHeaderRow(
        cells: List<WorksheetModel.Cell>,
        rows: Int,
        columns: Int,
    ): Int {
        var selected = 0
        var best = Double.NEGATIVE_INFINITY
        for (row in 0 until min(rows, 6)) {
            val rowCells = cells.filter { it.row == row && it.text.isNotEmpty() }
            if (rowCells.isEmpty()) continue
            val alpha = rowCells.count { cell -> cell.text.any { it.isLetter() } }
            val numeric = rowCells.count { cell -> cell.text.count { it.isDigit() } > cell.text.length / 2 }
            val score = alpha * 2.0 + rowCells.size.toDouble() / max(1, columns) - numeric
            if (score > best) {
                best = score
                selected = row
            }
        }
        return selected
    }

    private fun Iterable<Float>.averageOrNull(): Double? {
        var total = 0.0
        var count = 0
        for (value in this) {
            total += value
            count++
        }
        return if (count == 0) null else total / count
    }
}
