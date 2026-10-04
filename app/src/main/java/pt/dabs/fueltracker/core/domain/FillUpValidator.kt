package pt.dabs.fueltracker.core.domain

import pt.dabs.fueltracker.core.domain.model.FillUp
import java.time.Instant

enum class Severity { ERROR, WARNING }

/** Problemas de validação. Erros bloqueiam; avisos pedem confirmação. */
sealed interface ValidationIssue {
    val severity: Severity

    data object MissingOdometer : ValidationIssue { override val severity = Severity.ERROR }
    data object MissingVolume : ValidationIssue { override val severity = Severity.ERROR }
    data object MissingPrice : ValidationIssue { override val severity = Severity.ERROR }
    data object MissingTotal : ValidationIssue { override val severity = Severity.ERROR }

    /** Odómetro não é maior do que o do abastecimento anterior (por data). */
    data class OdometerNotAfterPrevious(val previousOdometerKm: Int) : ValidationIssue {
        override val severity = Severity.ERROR
    }

    /** Ao editar: odómetro não é menor do que o do abastecimento seguinte (por data). */
    data class OdometerNotBeforeNext(val nextOdometerKm: Int) : ValidationIssue {
        override val severity = Severity.ERROR
    }

    /** Consumo do ciclo fora do intervalo plausível. */
    data class ImplausibleConsumption(val consumptionMlPer100Km: Int) : ValidationIssue {
        override val severity = Severity.WARNING
    }

    /** litros × preço/litro difere do total além da tolerância. */
    data class TotalMismatch(val expectedTotalCents: Long) : ValidationIssue {
        override val severity = Severity.WARNING
    }

    data class VolumeExceedsTank(val tankCapacityMl: Int) : ValidationIssue {
        override val severity = Severity.WARNING
    }

    data object DuplicateAtcud : ValidationIssue { override val severity = Severity.WARNING }

    /** O talão foi emitido a "consumidor final", sem NIF. */
    data object ReceiptWithoutNif : ValidationIssue { override val severity = Severity.WARNING }

    /** O talão tem um NIF de adquirente diferente do configurado. */
    data class ReceiptNifMismatch(val buyerNif: String) : ValidationIssue {
        override val severity = Severity.WARNING
    }
}

/** Valores em edição; null = campo vazio ou inválido. */
data class FillUpDraft(
    val id: Long = FillUp.NEW_ID,
    val dateTime: Instant,
    val odometerKm: Int?,
    val volumeMl: Int?,
    val pricePerLiterMilliCents: Int?,
    val totalCents: Long?,
    val isFullTank: Boolean = true,
    val atcud: String? = null,
    /** NIF do adquirente lido do QR (campo B); null se não houve leitura. */
    val buyerNif: String? = null,
)

data class ValidationOptions(
    val minPlausibleMlPer100Km: Int = 2_000,
    val maxPlausibleMlPer100Km: Int = 25_000,
    val totalToleranceCents: Long = 5,
)

object FillUpValidator {

    /**
     * Valida um rascunho contra os abastecimentos já guardados.
     *
     * @param existing abastecimentos guardados; o que tem o mesmo id do rascunho (edição) é ignorado.
     * @param tankCapacityMl capacidade do depósito, se configurada.
     * @param atcudAlreadyUsed true se o ATCUD do rascunho já existe noutro abastecimento.
     * @param userNif NIF do utilizador configurado nas Definições, se houver.
     */
    fun validate(
        draft: FillUpDraft,
        existing: List<FillUp>,
        tankCapacityMl: Int? = null,
        atcudAlreadyUsed: Boolean = false,
        userNif: String? = null,
        options: ValidationOptions = ValidationOptions(),
    ): List<ValidationIssue> {
        val issues = ArrayList<ValidationIssue>()
        val others = ConsumptionCalculator.sortChronologically(existing.filter { it.id != draft.id })

        val odometer = draft.odometerKm?.takeIf { it > 0 }
        val volume = draft.volumeMl?.takeIf { it > 0 }
        val price = draft.pricePerLiterMilliCents?.takeIf { it > 0 }
        val total = draft.totalCents?.takeIf { it > 0 }

        if (odometer == null) issues += ValidationIssue.MissingOdometer
        if (volume == null) issues += ValidationIssue.MissingVolume
        if (price == null) issues += ValidationIssue.MissingPrice
        if (total == null) issues += ValidationIssue.MissingTotal

        val previous = others.lastOrNull { it.dateTime <= draft.dateTime }
        val next = others.firstOrNull { it.dateTime > draft.dateTime }

        if (odometer != null) {
            if (previous != null && odometer <= previous.odometerKm) {
                issues += ValidationIssue.OdometerNotAfterPrevious(previous.odometerKm)
            }
            if (next != null && odometer >= next.odometerKm) {
                issues += ValidationIssue.OdometerNotBeforeNext(next.odometerKm)
            }
        }

        if (volume != null && price != null && total != null) {
            val expected = FuelMath.totalCents(volume, price)
            if (kotlin.math.abs(expected - total) > options.totalToleranceCents) {
                issues += ValidationIssue.TotalMismatch(expected)
            }
        }

        if (volume != null && tankCapacityMl != null && tankCapacityMl > 0 && volume > tankCapacityMl) {
            issues += ValidationIssue.VolumeExceedsTank(tankCapacityMl)
        }

        if (odometer != null && volume != null && issues.none { it is ValidationIssue.OdometerNotAfterPrevious }) {
            val candidate = FillUp(
                id = if (draft.id == FillUp.NEW_ID) CANDIDATE_ID else draft.id,
                dateTime = draft.dateTime,
                odometerKm = odometer,
                volumeMl = volume,
                pricePerLiterMilliCents = price ?: 0,
                totalCents = total ?: 0,
                isFullTank = draft.isFullTank,
                createdAt = draft.dateTime,
            )
            val cycle = ConsumptionCalculator.computeCycles(others + candidate)
                .firstOrNull { it.fillUpId == candidate.id }
            if (cycle != null &&
                (cycle.consumptionMlPer100Km < options.minPlausibleMlPer100Km ||
                    cycle.consumptionMlPer100Km > options.maxPlausibleMlPer100Km)
            ) {
                issues += ValidationIssue.ImplausibleConsumption(cycle.consumptionMlPer100Km)
            }
        }

        if (!draft.atcud.isNullOrBlank() && atcudAlreadyUsed) {
            issues += ValidationIssue.DuplicateAtcud
        }

        val buyerNif = draft.buyerNif?.trim()
        if (!buyerNif.isNullOrEmpty()) {
            when {
                buyerNif == Nif.FINAL_CONSUMER -> issues += ValidationIssue.ReceiptWithoutNif
                !userNif.isNullOrBlank() && buyerNif != userNif -> issues += ValidationIssue.ReceiptNifMismatch(buyerNif)
            }
        }

        return issues
    }

    /** Abastecimento imediatamente anterior a [dateTime] (por data), ignorando [excludingId]. */
    fun previousFillUp(existing: List<FillUp>, dateTime: Instant, excludingId: Long = FillUp.NEW_ID): FillUp? =
        ConsumptionCalculator.sortChronologically(existing.filter { it.id != excludingId })
            .lastOrNull { it.dateTime <= dateTime }

    private const val CANDIDATE_ID = Long.MIN_VALUE
}

val List<ValidationIssue>.hasErrors: Boolean get() = any { it.severity == Severity.ERROR }
val List<ValidationIssue>.hasWarnings: Boolean get() = any { it.severity == Severity.WARNING }
