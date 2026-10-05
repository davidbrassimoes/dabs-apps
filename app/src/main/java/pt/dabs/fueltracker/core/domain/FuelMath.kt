package pt.dabs.fueltracker.core.domain

/**
 * Aritmética inteira para combustível e dinheiro. Arredondamento "half up".
 *
 * Unidades: ml (volume), km (distância), milésimos de cêntimo por litro (preço),
 * cêntimos (totais), ml/100 km (consumo), milésimos de cêntimo por km (custo).
 */
object FuelMath {

    /** Divisão inteira com arredondamento half-up. [divisor] tem de ser > 0. */
    fun roundDiv(numerator: Long, divisor: Long): Long {
        require(divisor > 0) { "divisor must be > 0" }
        return if (numerator >= 0) {
            (numerator + divisor / 2) / divisor
        } else {
            -((-numerator + divisor / 2) / divisor)
        }
    }

    /** Consumo em ml/100 km: volume / distância × 100. */
    fun consumptionMlPer100Km(volumeMl: Long, distanceKm: Int): Int {
        require(distanceKm > 0) { "distanceKm must be > 0" }
        return roundDiv(volumeMl * 100, distanceKm.toLong()).toInt()
    }

    /** Total em cêntimos a partir de ml e milésimos de cêntimo por litro. */
    fun totalCents(volumeMl: Int, pricePerLiterMilliCents: Int): Long =
        roundDiv(volumeMl.toLong() * pricePerLiterMilliCents, 1_000_000L)

    /** Preço/litro em milésimos de cêntimo a partir do total e do volume. */
    fun pricePerLiterMilliCents(totalCents: Long, volumeMl: Int): Int {
        require(volumeMl > 0) { "volumeMl must be > 0" }
        return roundDiv(totalCents * 1_000_000L, volumeMl.toLong()).toInt()
    }

    /** Volume em ml a partir do total e do preço/litro. */
    fun volumeMl(totalCents: Long, pricePerLiterMilliCents: Int): Int {
        require(pricePerLiterMilliCents > 0) { "price must be > 0" }
        return roundDiv(totalCents * 1_000_000L, pricePerLiterMilliCents.toLong()).toInt()
    }

    /** €/km em milésimos de cêntimo por km. */
    fun costMilliCentsPerKm(costCents: Long, distanceKm: Int): Long {
        require(distanceKm > 0) { "distanceKm must be > 0" }
        return roundDiv(costCents * 1000L, distanceKm.toLong())
    }

    /** Preço médio ponderado por litro (milésimos de cêntimo). */
    fun averagePricePerLiterMilliCents(totalCents: Long, volumeMl: Long): Int {
        require(volumeMl > 0) { "volumeMl must be > 0" }
        return roundDiv(totalCents * 1_000_000L, volumeMl).toInt()
    }
}
