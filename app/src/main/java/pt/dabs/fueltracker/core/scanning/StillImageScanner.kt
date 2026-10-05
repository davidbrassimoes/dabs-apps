package pt.dabs.fueltracker.core.scanning

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Corre o QR e o OCR sobre uma fotografia já tirada (galeria), com os mesmos modelos
 * empacotados usados em tempo real. A rotação EXIF é tratada pelo ML Kit.
 */
class StillImageScanner(private val context: Context) {

    data class ReceiptScan(val qrRaw: String?, val ocrRows: List<String>) {
        /** Sem QR, o talão só serve se o texto tiver pelo menos os litros ou o preço. */
        val isUsable: Boolean get() = qrRaw != null || hasFuelAmounts(ocrRows)
    }

    companion object {
        fun hasFuelAmounts(rows: List<String>): Boolean =
            ReceiptTextParser.parse(rows, totalCents = null).let { it.volumeMl != null || it.pricePerLiterMilliCents != null }
    }

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

    /**
     * Conta-km total numa fotografia do painel. Primeiro o leitor de sete segmentos (que lê o
     * visor de blocos do carro, onde o ML Kit falha); se não encontrar nada, o OCR genérico.
     */
    suspend fun readOdometer(uri: Uri, lastOdometerKm: Int?): Int? {
        val luma = withContext(Dispatchers.Default) {
            decodeLuma(uri)?.let { SevenSegmentReader.readOdometer(it, lastOdometerKm) }
        }
        return luma ?: OdometerReader.read(recognizeRows(uri), lastOdometerKm)
    }

    /** Luminância da fotografia, já rodada segundo o EXIF e reduzida para a análise. */
    private fun decodeLuma(uri: Uri): LumaImage? {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                // Bitmap em memória normal (getPixels não funciona em bitmaps de hardware).
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > SevenSegmentReader.MAX_SIDE) {
                    val scale = SevenSegmentReader.MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            // API 26-27: sem rotação EXIF (o ImageDecoder trata-a a partir da 28).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SevenSegmentReader.MAX_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return LumaImage.fromArgb(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
    }

    /** Linhas de texto da fotografia, já fundidas por faixa vertical. */
    suspend fun recognizeRows(uri: Uri): List<String> = recognizeText(uri).rows

    /**
     * Texto da fotografia tal como o ML Kit o devolve e já fundido por faixa vertical.
     * Usado também para diagnóstico (teste instrumentado das amostras e secção de debug).
     */
    suspend fun recognizeText(uri: Uri): RecognizedText {
        val image = InputImage.fromFilePath(context, uri)
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = textRecognizer.process(image).await()
            val lines = text.toOcrLines()
            return RecognizedText(image.width, image.height, text.text, lines, OcrRows.merge(lines))
        } finally {
            textRecognizer.close()
        }
    }

    data class RecognizedText(
        val imageWidth: Int,
        val imageHeight: Int,
        val fullText: String,
        val lines: List<OcrLine>,
        val rows: List<String>,
    )

    private fun Text.toRows(): List<String> = OcrRows.merge(toOcrLines())

    private fun Text.toOcrLines(): List<OcrLine> =
        textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { box -> OcrLine(line.text, box.top, box.bottom, box.left) }
        }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
