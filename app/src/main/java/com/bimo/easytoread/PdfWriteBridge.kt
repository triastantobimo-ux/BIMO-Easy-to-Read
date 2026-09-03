package com.bimo.easytoread

import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfWriteHandle
import kotlinx.coroutines.runBlocking

/** Keeps the Java save pipeline synchronous on its dedicated worker thread. */
@OptIn(ExperimentalPdfApi::class)
internal object PdfWriteBridge {
    @JvmStatic
    fun writeBlocking(handle: PdfWriteHandle, target: ParcelFileDescriptor) {
        runBlocking {
            handle.writeTo(target)
        }
    }
}

