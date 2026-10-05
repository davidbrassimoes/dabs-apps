package pt.dabs.fueltracker.core.scanning

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Corre o OCR do ML Kit sobre as fotografias reais em `test-photos/` (empacotadas como assets
 * do APK de teste, só quando a pasta existe) e escreve o texto reconhecido, para afinar os
 * parsers com o que o ML Kit devolve de facto.
 *
 * Saída: logcat com a etiqueta [TAG] e um ficheiro por foto em
 * `Android/data/pt.dabs.fueltracker/files/ocr-dump/` (correr com `am instrument`, que não
 * desinstala a app, e depois `adb pull`). Não faz asserções: é uma ferramenta de diagnóstico.
 */
@RunWith(AndroidJUnit4::class)
class SamplePhotoOcrDumpTest {

    @Test
    fun dumpRecognizedText() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val photos = testAssets.list("")?.filter { it.endsWith(".jpg", ignoreCase = true) }.orEmpty()
        assumeTrue("sem fotografias em test-photos/", photos.isNotEmpty())

        val target = instrumentation.targetContext
        val outputDir = File(target.getExternalFilesDir(null), "ocr-dump").apply { mkdirs() }
        val scanner = StillImageScanner(target)
        for (name in photos.sorted()) {
            // Copia para um ficheiro para seguir o mesmo caminho da galeria (InputImage.fromFilePath, EXIF).
            val file = File(target.cacheDir, name)
            testAssets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
            val text = scanner.recognizeText(Uri.fromFile(file))
            val receipt = scanner.scanReceipt(Uri.fromFile(file))
            val odometer = scanner.readOdometer(Uri.fromFile(file), lastOdometerKm = null)

            val report = buildString {
                appendLine("# $name ${text.imageWidth}x${text.imageHeight}")
                appendLine("## QR")
                appendLine(receipt.qrRaw ?: "(sem QR)")
                appendLine("## Conta-km (sete segmentos, OCR como recurso)")
                appendLine(odometer?.toString() ?: "(nenhum)")
                appendLine("## Linhas ML Kit (top,bottom,left | texto)")
                text.lines.forEach { appendLine("${it.top},${it.bottom},${it.left} | ${it.text}") }
                appendLine("## Linhas fundidas (OcrRows.merge)")
                text.rows.forEach { appendLine(it) }
            }
            File(outputDir, "$name.txt").writeText(report)
            report.lineSequence().forEach { Log.i(TAG, it) }
            file.delete()
        }
    }

    private companion object {
        const val TAG = "OcrDump"
    }
}
