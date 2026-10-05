package pt.dabs.fueltracker.core.domain.model

import java.time.Instant

/**
 * Um abastecimento. Todos os valores monetários e volumes são inteiros:
 * - [volumeMl]: mililitros (litros × 1000)
 * - [pricePerLiterMilliCents]: milésimos de cêntimo por litro (1,749 €/L = 174 900)
 * - [totalCents]: cêntimos; total pago (o do QR, já com os descontos do talão)
 * - [discountCents]: cêntimos; descontos do talão, positivos. litros × preço = total + desconto
 * - [tripTenthsKm]: décimas de km; conta-km parcial lido ao abastecer (antes de o pôr a zeros),
 *   ou seja a distância do ciclo; null se não foi registado
 */
data class FillUp(
    val id: Long = NEW_ID,
    val dateTime: Instant,
    val odometerKm: Int,
    val volumeMl: Int,
    val pricePerLiterMilliCents: Int,
    val totalCents: Long,
    val discountCents: Long = 0,
    val tripTenthsKm: Int? = null,
    val isFullTank: Boolean = true,
    val stationNif: String? = null,
    val stationName: String? = null,
    /** Marca do posto; null se desconhecida. O local fica em [stationName] (texto livre). */
    val brand: FuelBrand? = null,
    val fuelType: FuelType? = null,
    /** Combustível aditivado; null se desconhecido. */
    val fuelPremium: Boolean? = null,
    val atcud: String? = null,
    val buyerNifPresent: Boolean? = null,
    val notes: String? = null,
    val createdAt: Instant,
) {
    companion object {
        const val NEW_ID = 0L
    }
}
