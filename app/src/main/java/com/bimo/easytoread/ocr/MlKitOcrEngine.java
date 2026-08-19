package com.bimo.easytoread.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.bimo.easytoread.core.Box;
import com.bimo.easytoread.core.DetectedLine;
import com.bimo.easytoread.core.DocumentModel;
import com.bimo.easytoread.core.DocumentStructureEngine;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;

public final class MlKitOcrEngine implements OcrEngine {
    public static final String ENGINE_ID = "mlkit-latin-bundled-16.0.1";

    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final DocumentStructureEngine structureEngine = new DocumentStructureEngine();

    @Override
    public void recognize(Bitmap bitmap, Callback callback) {
        InputImage input = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(input)
                .addOnSuccessListener(text -> callback.onSuccess(toDocument(text)))
                .addOnFailureListener(callback::onFailure);
    }

    private DocumentModel toDocument(Text text) {
        List<DetectedLine> lines = new ArrayList<>();
        int fallbackY = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect rect = line.getBoundingBox();
                if (rect == null) rect = block.getBoundingBox();
                Box box;
                if (rect == null) {
                    box = new Box(0, fallbackY, Math.max(1, line.getText().length()), fallbackY + 1);
                    fallbackY += 2;
                } else {
                    box = new Box(rect.left, rect.top, rect.right, rect.bottom);
                }
                Float confidence = line.getConfidence();
                lines.add(new DetectedLine(
                        line.getText(),
                        box,
                        confidence == null ? -1f : confidence
                ));
            }
        }
        return structureEngine.structure(ENGINE_ID, lines);
    }

    @Override
    public void close() {
        recognizer.close();
    }
}
