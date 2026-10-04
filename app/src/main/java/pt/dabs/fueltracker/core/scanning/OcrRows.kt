package pt.dabs.fueltracker.core.scanning

/** Linha reconhecida pelo OCR com a sua caixa (coordenadas de imagem, origem no canto superior esquerdo). */
data class OcrLine(val text: String, val top: Int, val bottom: Int, val left: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = maxOf(1, bottom - top)
}

/**
 * Junta linhas que estão na mesma faixa vertical (colunas da mesma linha do talão) numa só
 * linha de texto, da esquerda para a direita, e devolve as linhas de cima para baixo.
 *
 * O ML Kit devolve blocos por coluna; sem esta fusão, "40,12 L" e "1,749 €/L" da mesma linha
 * do talão chegariam ao parser separados e sem relação entre si.
 */
object OcrRows {

    fun merge(lines: List<OcrLine>, toleranceRatio: Double = 0.5): List<String> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }
        val rows = ArrayList<MutableList<OcrLine>>()
        var currentCenter = 0
        var currentHeight = 0
        for (line in sorted) {
            val current = rows.lastOrNull()
            val tolerance = ((currentHeight + line.height) / 2 * toleranceRatio).toInt()
            if (current != null && kotlin.math.abs(line.centerY - currentCenter) <= tolerance) {
                current += line
                currentCenter = current.sumOf { it.centerY } / current.size
                currentHeight = current.sumOf { it.height } / current.size
            } else {
                rows += mutableListOf(line)
                currentCenter = line.centerY
                currentHeight = line.height
            }
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }
}
