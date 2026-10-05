package pt.dabs.fueltracker.core.scanning

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Corre o QR e o OCR sobre uma fotografia já tirada (galeria), com os mesmos modelos
 * empacotados usados em tempo real. A rotação EXIF é tratada pelo ML Kit.
 */
class StillImageScanner(private val context: Context) {

    data class ReceiptScan(val qrRaw: String?, val ocrRows: List<String>)

    suspend fun scanReceipt(uri: Uri): ReceiptScan {
        val image = InputImage.fromFilePath(context, uri)
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val qrRaw = barcodeScanner.process(image).await()
                .firstNotNullOfOrNull { it.rawValue?.takeIf { raw -> raw.isNotBlank() } }
            val rows = textRecognizer.process(image).await().toRows()
            return ReceiptScan(qrRaw, rows)
        } finally {
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    /** Linhas de texto da fotografia, já fundidas por faixa vertical. */
    suspend fun recognizeRows(uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return textRecognizer.process(image).await().toRows()
        } finally {
            textRecognizer.close()
        }
    }

    private fun Text.toRows(): List<String> = OcrRows.merge(
        textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { box -> OcrLine(line.text, box.top, box.bottom, box.left) }
        }
    )
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
