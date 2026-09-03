package com.bimo.easytoread;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.pdf.ExperimentalPdfApi;
import androidx.pdf.PdfDocument;
import androidx.pdf.view.PdfView;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

@OptIn(markerClass = ExperimentalPdfApi.class)
public final class ReadOnlyPdfFragment extends PdfViewerFragment {
    private PdfWorkspaceHost host;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof PdfWorkspaceHost)) {
            throw new IllegalStateException("Host must implement PdfWorkspaceHost");
        }
        host = (PdfWorkspaceHost) context;
    }

    @Override
    public void onPdfViewCreated(@NonNull PdfView pdfView) {
        super.onPdfViewCreated(pdfView);
        host.onPdfViewCreated(pdfView);
    }

    @Override
    public void onLoadDocumentSuccess(@NonNull PdfDocument document) {
        super.onLoadDocumentSuccess(document);
        host.onPdfDocumentLoaded(document);
    }

    @Override
    public void onLoadDocumentError(@NonNull Throwable error) {
        super.onLoadDocumentError(error);
        host.onPdfDocumentError(error);
    }

    @Override
    public void onDetach() {
        host = null;
        super.onDetach();
    }
}

