package com.bimo.easytoread;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresExtension;
import androidx.pdf.ExperimentalPdfApi;
import androidx.pdf.PdfDocument;
import androidx.pdf.PdfWriteHandle;
import androidx.pdf.ink.EditablePdfViewerFragment;
import androidx.pdf.view.PdfView;

@OptIn(markerClass = ExperimentalPdfApi.class)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
public final class EditablePdfFragment extends EditablePdfViewerFragment {
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
        pdfView.setFormFillingEnabled(true);
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
    public void onApplyEditsSuccess(@NonNull PdfWriteHandle handle) {
        super.onApplyEditsSuccess(handle);
        host.onPdfWriteReady(handle);
    }

    @Override
    public void onApplyEditsFailed(@NonNull Throwable error) {
        super.onApplyEditsFailed(error);
        host.onPdfWriteFailed(error);
    }

    @Override
    public void onEnterEditMode() {
        super.onEnterEditMode();
        host.onPdfEditModeChanged(true);
    }

    @Override
    public void onExitEditMode() {
        super.onExitEditMode();
        host.onPdfEditModeChanged(false);
    }

    @Override
    public void onDetach() {
        host = null;
        super.onDetach();
    }
}

