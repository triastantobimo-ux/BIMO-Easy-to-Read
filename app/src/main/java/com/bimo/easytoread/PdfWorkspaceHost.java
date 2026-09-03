package com.bimo.easytoread;

import androidx.pdf.PdfDocument;
import androidx.pdf.PdfWriteHandle;
import androidx.pdf.view.PdfView;

interface PdfWorkspaceHost {
    void onPdfViewCreated(PdfView pdfView);

    void onPdfDocumentLoaded(PdfDocument document);

    void onPdfDocumentError(Throwable error);

    void onPdfWriteReady(PdfWriteHandle handle);

    void onPdfWriteFailed(Throwable error);

    void onPdfEditModeChanged(boolean enabled);
}

