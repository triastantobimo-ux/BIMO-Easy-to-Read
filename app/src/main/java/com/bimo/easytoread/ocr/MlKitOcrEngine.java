package com.bimo.easytoread.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.bimo.easytoread.core.Box;
import com.bimo.easytoread.core.DetectedLine;
import com.bimo.easytoread.core.DetectedToken;
import com.bimo.easytoread.core.DocumentModel;
import com.bimo.easytoread.core.DocumentStructureEngine;
import com.bimo.easytoread.core.TextLineNoiseFilter;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MlKitOcrEngine implements OcrEngine {
    public static final String ENGINE_ID =
            "mlkit-latin-bundled-16.0.1-layout-aware-cell-enhanced";
    private static final int[] ROTATIONS = {0, 180, 90, 270};

    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final DocumentStructureEngine structureEngine = new DocumentStructureEngine();
    private final ExecutorService processingExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void recognize(Bitmap bitmap, Callback callback) {
        recognizeRotation(bitmap, 0, new ArrayList<>(), null, callback);
    }

    private void recognizeRotation(
            Bitmap bitmap,
            int rotationIndex,
            List<Candidate> candidates,
            Throwable lastFailure,
            Callback callback
    ) {
        if (rotationIndex >= ROTATIONS.length) {
            finishCandidates(bitmap, candidates, lastFailure, callback);
            return;
        }

        int rotation = ROTATIONS[rotationIndex];
        recognizer.process(InputImage.fromBitmap(bitmap, rotation))
                .addOnSuccessListener(processingExecutor, text -> {
                    candidates.add(new Candidate(rotation, "original", text, qualityScore(text)));
                    recognizeRotation(
                            bitmap,
                            rotationIndex + 1,
                            candidates,
                            lastFailure,
                            callback
                    );
                })
                .addOnFailureListener(processingExecutor, error -> recognizeRotation(
                        bitmap,
                        rotationIndex + 1,
                        candidates,
                        error,
                        callback
                ));
    }

    private void finishCandidates(
            Bitmap bitmap,
            List<Candidate> candidates,
            Throwable lastFailure,
            Callback callback
    ) {
        if (candidates.isEmpty()) {
            callback.onFailure(lastFailure == null
                    ? new IllegalStateException("OCR returned no orientation candidates.")
                    : lastFailure);
            return;
        }

        Candidate bestOriginal = bestCandidate(candidates);
        final Bitmap enhanced;
        try {
            enhanced = ImagePreprocessor.enhanceForSceneText(bitmap);
        } catch (Throwable preprocessingFailure) {
            callback.onSuccess(toDocument(bestOriginal));
            return;
        }

        recognizer.process(InputImage.fromBitmap(enhanced, bestOriginal.rotation))
                .addOnSuccessListener(processingExecutor, text -> {
                    Candidate enhancedCandidate = new Candidate(
                            bestOriginal.rotation,
                            "enhanced",
                            text,
                            qualityScore(text)
                    );
                    Candidate best = enhancedCandidate.score > bestOriginal.score
                            ? enhancedCandidate
                            : bestOriginal;
                    enhanced.recycle();
                    callback.onSuccess(toDocument(best));
                })
                .addOnFailureListener(processingExecutor, error -> {
                    enhanced.recycle();
                    callback.onSuccess(toDocument(bestOriginal));
                });
    }

    private static Candidate bestCandidate(List<Candidate> candidates) {
        Candidate best = candidates.get(0);
        for (int index = 1; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            if (candidate.score > best.score) best = candidate;
        }
        return best;
    }

    private DocumentModel toDocument(Candidate candidate) {
        List<DetectedLine> lines = new ArrayList<>();
        int fallbackY = 0;
        for (Text.TextBlock block : candidate.text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String cleanText = sanitize(line.getText());
                if (cleanText.isEmpty()) continue;

                List<DetectedToken> tokens = new ArrayList<>();
                for (Text.Element element : line.getElements()) {
                    String tokenText = sanitize(element.getText());
                    Rect tokenRect = element.getBoundingBox();
                    if (tokenText.isEmpty() || tokenRect == null) continue;
                    Float tokenConfidence = element.getConfidence();
                    tokens.add(new DetectedToken(
                            tokenText,
                            new Box(tokenRect.left, tokenRect.top, tokenRect.right, tokenRect.bottom),
                            tokenConfidence == null ? -1f : tokenConfidence
                    ));
                }

                Rect rect = line.getBoundingBox();
                if (rect == null) rect = block.getBoundingBox();
                Box box;
                if (rect == null && !tokens.isEmpty()) {
                    box = tokens.get(0).getBox();
                    for (int index = 1; index < tokens.size(); index++) {
                        box = box.union(tokens.get(index).getBox());
                    }
                } else if (rect == null) {
                    box = new Box(
                            0,
                            fallbackY,
                            Math.max(1, cleanText.length()),
                            fallbackY + 1
                    );
                    fallbackY += 2;
                } else {
                    box = new Box(rect.left, rect.top, rect.right, rect.bottom);
                }
                Float confidence = line.getConfidence();
                lines.add(new DetectedLine(
                        cleanText,
                        box,
                        confidence == null ? -1f : confidence,
                        tokens
                ));
            }
        }

        List<DetectedLine> filtered = TextLineNoiseFilter.filter(lines);
        String trace = ENGINE_ID
                + "|rotation=" + candidate.rotation
                + "|variant=" + candidate.variant
                + "|raw-lines=" + lines.size()
                + "|kept-lines=" + filtered.size();
        return structureEngine.structure(trace, filtered);
    }

    static double qualityScore(Text text) {
        int totalCharacters = 0;
        int readableCharacters = 0;
        int suspiciousCharacters = 0;
        int tokenCount = 0;
        int wordLikeTokens = 0;
        int shortLines = 0;
        int lineCount = 0;
        double confidenceTotal = 0;
        int confidenceCount = 0;

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String clean = sanitize(line.getText());
                if (clean.isEmpty()) continue;
                lineCount++;
                if (clean.length() <= 2) shortLines++;

                Float confidence = line.getConfidence();
                if (confidence != null && confidence >= 0f) {
                    confidenceTotal += confidence;
                    confidenceCount++;
                }

                for (int index = 0; index < clean.length(); index++) {
                    char value = clean.charAt(index);
                    totalCharacters++;
                    if (Character.isLetterOrDigit(value)
                            || Character.isWhitespace(value)
                            || isCommonPunctuation(value)) {
                        readableCharacters++;
                    } else {
                        suspiciousCharacters++;
                    }
                }

                String[] tokens = clean.split("\\s+");
                for (String token : tokens) {
                    if (token.isEmpty()) continue;
                    tokenCount++;
                    int alphaNumeric = 0;
                    for (int index = 0; index < token.length(); index++) {
                        if (Character.isLetterOrDigit(token.charAt(index))) alphaNumeric++;
                    }
                    if (alphaNumeric >= 2
                            && (double) alphaNumeric / token.length() >= 0.65) {
                        wordLikeTokens++;
                    }
                }
            }
        }

        double confidence = confidenceCount == 0
                ? 0.62
                : confidenceTotal / confidenceCount;
        double readability = totalCharacters == 0
                ? 0
                : (double) readableCharacters / totalCharacters;
        double wordRatio = tokenCount == 0
                ? 0
                : (double) wordLikeTokens / tokenCount;

        return confidence * 60.0
                + readability * 22.0
                + wordRatio * 26.0
                + Math.min(lineCount, 35) * 0.45
                + Math.min(totalCharacters, 1200) * 0.01
                - suspiciousCharacters * 0.9
                - shortLines * 0.45;
    }

    static String sanitize(String source) {
        if (source == null) return "";
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFKC);
        StringBuilder clean = new StringBuilder(normalized.length());
        boolean previousSpace = false;

        for (int index = 0; index < normalized.length(); index++) {
            char value = normalized.charAt(index);
            int type = Character.getType(value);
            if (value == '\uFFFD'
                    || type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE) {
                continue;
            }

            if (Character.isWhitespace(value)) {
                if (!previousSpace && clean.length() > 0) clean.append(' ');
                previousSpace = true;
            } else {
                clean.append(value);
                previousSpace = false;
            }
        }
        return clean.toString().trim();
    }

    private static boolean isCommonPunctuation(char value) {
        return ".,:;!?()[]{}'\"/\\-–—_+%#@&*=<>|•·".indexOf(value) >= 0;
    }

    @Override
    public void close() {
        recognizer.close();
        processingExecutor.shutdownNow();
    }

    private static final class Candidate {
        private final int rotation;
        private final String variant;
        private final Text text;
        private final double score;

        Candidate(int rotation, String variant, Text text, double score) {
            this.rotation = rotation;
            this.variant = variant;
            this.text = text;
            this.score = score;
        }
    }
}

