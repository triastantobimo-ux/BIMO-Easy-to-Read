package com.bimo.easytoread.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.bimo.easytoread.core.Box
import com.bimo.easytoread.core.DetectedLine
import com.bimo.easytoread.core.DetectedToken
import com.bimo.easytoread.core.DocumentModel
import com.bimo.easytoread.core.DocumentStructureEngine
import com.bimo.easytoread.core.TextLineNoiseFilter
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

class PaddleOcrEngine(context: Context) : OcrEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initializationMutex = Mutex()
    private val structureEngine = DocumentStructureEngine()
    private val orientationProbe = MlKitOcrEngine()
    @Volatile private var paddle: PaddleOCR? = null
    @Volatile private var closed = false

    override fun recognize(bitmap: Bitmap, callback: OcrEngine.Callback) {
        if (closed) {
            callback.onFailure(IllegalStateException("OCR engine is closed."))
            return
        }
        orientationProbe.recognize(bitmap, object : OcrEngine.Callback {
            override fun onSuccess(document: DocumentModel) {
                scope.launch {
                    runPrimary(bitmap, document, callback)
                }
            }

            override fun onFailure(error: Throwable) {
                scope.launch {
                    runPrimary(bitmap, null, callback)
                }
            }
        })
    }

    private suspend fun runPrimary(
        source: Bitmap,
        baseline: DocumentModel?,
        callback: OcrEngine.Callback,
    ) {
        val rotation = baseline?.engineId
            ?.let { Regex("""\|rotation=(\d+)""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: 0
        val bitmap = rotate(source, rotation)
        try {
            val engine = getPaddle()
            val quality = ImageQualityAnalyzer.analyze(bitmap)
            val result = engine.recognize(bitmap)
            val document = toDocument(result.results, result, quality, rotation, engine, bitmap)
            if (document.countLines() == 0 && baseline != null) callback.onSuccess(baseline)
            else callback.onSuccess(document)
        } catch (failure: Throwable) {
            if (baseline != null) callback.onSuccess(baseline)
            else callback.onFailure(failure)
        } finally {
            if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private suspend fun getPaddle(): PaddleOCR {
        paddle?.let { return it }
        return initializationMutex.withLock {
            paddle?.let { return@withLock it }
            check(OpenCVUtils.init(appContext)) { "Bundled OpenCV runtime failed to initialize." }
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
            PaddleOCR.create(
                context = appContext,
                config = PaddleOCRConfig(
                    detLimitSideLen = 2240,
                    detLimitType = "max",
                    detMaxSideLimit = 4096,
                    detThresh = 0.28f,
                    detBoxThresh = 0.52f,
                    detUnclipRatio = 1.55f,
                    detScoreMode = "slow",
                    recScoreThresh = 0.0f,
                    recBatchSize = 8,
                ),
                engineConfig = EngineConfig(numThreads = threads),
                detModelAssetPath = "models/det/inference.onnx",
                recModelAssetPath = "models/rec/inference.onnx",
                recConfigAssetPath = "models/rec/inference.yml",
            ).also { paddle = it }
        }
    }

    private suspend fun toDocument(
        results: List<OCRResult>,
        run: com.paddle.ocr.model.OCRRunResult,
        quality: ImageQualityAnalyzer.Report,
        rotation: Int,
        engine: PaddleOCR,
        bitmap: Bitmap,
    ): DocumentModel {
        val lines = results.mapNotNull { toLine(it) }
        val filtered = TextLineNoiseFilter.filter(lines)
        val worksheet = GridTableExtractor.extract(bitmap, engine, results)
        val trace = "pp-ocrv6-medium-onnx-accuracy" +
                "|rotation=" + rotation +
                "|quality=" + quality.traceValue() +
                "|det-ms=" + run.detectionTimeMs +
                "|rec-ms=" + run.recognitionTimeMs +
                "|raw-lines=" + lines.size +
                "|kept-lines=" + filtered.size +
                "|worksheet=" + (worksheet?.verificationStatus?.name ?: "none")
        val structured = structureEngine.structure(trace, filtered)
        return DocumentModel(structured.engineId, structured.blocks, worksheet)
    }

    private fun toLine(result: OCRResult): DetectedLine? {
        val text = MlKitOcrEngine.sanitize(result.text)
        if (text.isEmpty()) return null
        val xs = result.box.points.map { it.x }
        val ys = result.box.points.map { it.y }
        val left = (xs.minOrNull() ?: 0f).toInt()
        val right = max(left + 1, (xs.maxOrNull() ?: left + 1f).toInt())
        val top = (ys.minOrNull() ?: 0f).toInt()
        val bottom = max(top + 1, (ys.maxOrNull() ?: top + 1f).toInt())
        val box = Box(left, top, right, bottom)
        return DetectedLine(text, box, result.confidence, approximateTokens(text, box, result.confidence))
    }

    private fun approximateTokens(text: String, box: Box, confidence: Float): List<DetectedToken> {
        val matches = Regex("""\S+""").findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        val total = max(1, text.length)
        val width = max(1, box.width())
        return matches.map { match ->
            val tokenLeft = box.left + width * match.range.first / total
            val tokenRight = box.left + width * (match.range.last + 1) / total
            DetectedToken(
                match.value,
                Box(tokenLeft, box.top, max(tokenLeft + 1, tokenRight), box.bottom),
                confidence,
            )
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    override fun close() {
        if (closed) return
        closed = true
        orientationProbe.close()
        val active = paddle
        paddle = null
        if (active != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                active.release()
            }
        }
        scope.cancel()
    }
}
