package pt.dabs.fueltracker.feature.fillup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.dabs.fueltracker.core.data.FillUpRepository
import pt.dabs.fueltracker.core.data.SettingsRepository
import pt.dabs.fueltracker.core.domain.ConsumptionCalculator
import pt.dabs.fueltracker.core.domain.FillUpDraft
import pt.dabs.fueltracker.core.domain.FillUpValidator
import pt.dabs.fueltracker.core.domain.FuelMath
import pt.dabs.fueltracker.core.domain.Nif
import pt.dabs.fueltracker.core.domain.NumberInput
import pt.dabs.fueltracker.core.domain.ValidationIssue
import pt.dabs.fueltracker.core.domain.hasErrors
import pt.dabs.fueltracker.core.domain.hasWarnings
import pt.dabs.fueltracker.core.domain.model.FillUp
import pt.dabs.fueltracker.core.scanning.AmountSource
import pt.dabs.fueltracker.core.scanning.AtQrCode
import pt.dabs.fueltracker.core.scanning.AtQrCodeParser
import pt.dabs.fueltracker.core.scanning.AtQrParseResult
import pt.dabs.fueltracker.core.scanning.ReceiptAmounts
import pt.dabs.fueltracker.core.scanning.ReceiptTextParser
import pt.dabs.fueltracker.navigation.FillUpRoute
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

enum class AmountField { VOLUME, PRICE, TOTAL }

enum class OcrOutcome { VOLUME_AND_PRICE, VOLUME_ONLY, PRICE_ONLY, NOTHING }

/** O que a câmara devolveu na última leitura de talão (só para diagnóstico). */
data class OcrDebug(val qrRaw: String?, val rows: List<String>)

/** Dados do talão lidos do QR da AT e associados ao abastecimento. */
data class ScannedReceipt(
    val issuerNif: String,
    val buyerNif: String,
    val atcud: String,
    val documentId: String?,
    val documentDate: LocalDate,
    val totalCents: Long,
)

data class FillUpUiState(
    val isLoading: Boolean = true,
    val isEdit: Boolean = false,
    val date: LocalDate,
    val time: LocalTime,
    val odometerText: String = "",
    val volumeText: String = "",
    val priceText: String = "",
    val totalText: String = "",
    /** Descontos do talão; vazio = sem desconto. */
    val discountText: String = "",
    val isFullTank: Boolean = true,
    val stationName: String = "",
    val notes: String = "",
    /** Odómetro do abastecimento anterior (por data), para sugestão. */
    val previousOdometerKm: Int? = null,
    /** Odómetro previsto (anterior + distância média por depósito), para pré-preencher. */
    val predictedOdometerKm: Int? = null,
    /** Fluxo rápido: o ecrã deve abrir a leitura do talão assim que puder. */
    val pendingReceiptScan: Boolean = false,
    /** Depois do talão, abrir a leitura do conta-km se ainda não estiver preenchido. */
    val pendingOdometerScan: Boolean = false,
    /** Talão lido nesta sessão ou guardado com o abastecimento em edição. */
    val receipt: ScannedReceipt? = null,
    /** Última leitura de QR falhou (não era um QR da AT ou estava incompleto). */
    val scanFailed: Boolean = false,
    /** O que o OCR conseguiu ler na última leitura de talão; null se ainda não houve leitura. */
    val ocrOutcome: OcrOutcome? = null,
    /** Desconto lido do talão na última leitura (para o informar), se houver. */
    val ocrDiscountCents: Long? = null,
    /** A última leitura não tinha QR: valores e total vieram só do texto do talão. */
    val receiptTextOnly: Boolean = false,
    /** Texto bruto da última leitura de talão, para a secção de debug. */
    val ocrDebug: OcrDebug? = null,
    val issues: List<ValidationIssue> = emptyList(),
    /** Só mostrar erros depois de o utilizador tentar guardar ou mexer no campo. */
    val showErrors: Boolean = false,
    /** Pedir confirmação dos avisos antes de guardar. */
    val askWarningConfirmation: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && !isSaving && !issues.hasErrors
}

@HiltViewModel
class FillUpViewModel(
    private val fillUpId: Long,
    startWithScan: Boolean,
    private val repository: FillUpRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: FillUpRepository,
        settingsRepository: SettingsRepository,
        clock: Clock,
    ) : this(
        fillUpId = savedStateHandle.toRoute<FillUpRoute>().fillUpId,
        startWithScan = savedStateHandle.toRoute<FillUpRoute>().startWithScan,
        repository = repository,
        settingsRepository = settingsRepository,
        clock = clock,
    )

    private val _uiState = MutableStateFlow(
        FillUpUiState(
            isEdit = fillUpId != FillUp.NEW_ID,
            date = LocalDate.now(clock),
            time = LocalTime.now(clock).withSecond(0).withNano(0),
            pendingReceiptScan = startWithScan && fillUpId == FillUp.NEW_ID,
        )
    )
    val uiState: StateFlow<FillUpUiState> = _uiState.asStateFlow()

    /** Campos de valor que o utilizador editou; o terceiro é derivado dos outros dois. */
    private val touched = LinkedHashSet<AmountField>()
    private var existing: List<FillUp> = emptyList()
    private var original: FillUp? = null
    private var tankCapacityMl: Int? = null
    private var userNif: String? = null
    private var atcudAlreadyUsed = false

    init {
        viewModelScope.launch {
            existing = repository.observeAll().first()
            val settings = settingsRepository.settings.first()
            tankCapacityMl = settings.tankCapacityMl
            userNif = settings.nif
            val loaded = if (fillUpId != FillUp.NEW_ID) repository.getById(fillUpId) else null
            original = loaded
            if (loaded != null) {
                touched += listOf(AmountField.VOLUME, AmountField.PRICE, AmountField.TOTAL)
                val local = LocalDateTime.ofInstant(loaded.dateTime, clock.zone)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        date = local.toLocalDate(),
                        time = local.toLocalTime().withSecond(0).withNano(0),
                        odometerText = loaded.odometerKm.toString(),
                        volumeText = NumberInput.formatScaled(loaded.volumeMl.toLong(), 3, trimTrailingZeros = true),
                        priceText = NumberInput.formatScaled(loaded.pricePerLiterMilliCents.toLong(), 5, trimTrailingZeros = true),
                        totalText = NumberInput.formatScaled(loaded.totalCents, 2),
                        discountText = if (loaded.discountCents > 0) NumberInput.formatScaled(loaded.discountCents, 2) else "",
                        isFullTank = loaded.isFullTank,
                        stationName = loaded.stationName.orEmpty(),
                        notes = loaded.notes.orEmpty(),
                        receipt = loaded.toStoredReceipt(),
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
            revalidate()
        }
    }

    fun onDateChange(date: LocalDate) = edit { copy(date = date) }
    fun onTimeChange(time: LocalTime) = edit { copy(time = time) }
    fun onOdometerChange(text: String) = edit { copy(odometerText = text) }
    fun onFullTankChange(full: Boolean) = edit { copy(isFullTank = full) }
    fun onStationNameChange(text: String) = edit { copy(stationName = text) }
    fun onNotesChange(text: String) = edit { copy(notes = text) }

    fun onVolumeChange(text: String) = onAmountChange(AmountField.VOLUME, text)
    fun onPriceChange(text: String) = onAmountChange(AmountField.PRICE, text)
    fun onTotalChange(text: String) = onAmountChange(AmountField.TOTAL, text)

    /** O desconto não conta como valor "escrito": entra no cálculo do valor em falta. */
    fun onDiscountChange(text: String) {
        _uiState.update { it.copy(discountText = text) }
        deriveMissingAmount()
        revalidate()
    }

    /**
     * Texto lido de um código QR. Se for um QR da AT, preenche data, total e dados do talão;
     * os valores ficam sempre editáveis e sujeitos a confirmação.
     */
    fun onQrScanned(raw: String) = onReceiptScanned(raw, emptyList())

    /**
     * Leitura completa do talão: texto do QR e linhas reconhecidas por OCR na mesma imagem.
     * Sem QR ([qrRaw] null ou vazio) usam-se só os valores e o total impressos.
     * Os valores lidos são só sugestões; o utilizador confirma.
     */
    fun onReceiptScanned(qrRaw: String?, ocrRows: List<String>) {
        _uiState.update { it.copy(ocrDebug = OcrDebug(qrRaw?.takeIf { raw -> raw.isNotBlank() }, ocrRows)) }
        if (qrRaw.isNullOrBlank()) {
            applyTextOnlyReceipt(ocrRows)
            return
        }
        when (val result = AtQrCodeParser.parse(qrRaw)) {
            is AtQrParseResult.Failure -> _uiState.update { it.copy(scanFailed = true) }
            is AtQrParseResult.Success -> {
                applyReceipt(result.code)
                applyOcr(ReceiptTextParser.parse(ocrRows, result.code.totalCents))
            }
        }
    }

    /** Talão sem QR legível: o total impresso faz as vezes do total do QR. */
    private fun applyTextOnlyReceipt(ocrRows: List<String>) {
        val amounts = ReceiptTextParser.parse(ocrRows, totalCents = null)
        val printedTotal = amounts.printedTotalCents
        val current = _uiState.value
        if (printedTotal != null) {
            touched.clear()
            if (current.volumeText.isNotBlank()) touched += AmountField.VOLUME
            if (current.priceText.isNotBlank()) touched += AmountField.PRICE
            touched += AmountField.TOTAL
        }
        _uiState.update {
            it.copy(
                scanFailed = false,
                receipt = null,
                receiptTextOnly = true,
                totalText = printedTotal?.let { total -> NumberInput.formatScaled(total, 2) } ?: it.totalText,
                pendingOdometerScan = it.odometerText.isBlank(),
            )
        }
        applyOcr(amounts)
    }

    private fun applyOcr(amounts: ReceiptAmounts) {
        val volumeRead = amounts.volumeSource == AmountSource.OCR && amounts.volumeMl != null
        val priceRead = amounts.priceSource == AmountSource.OCR && amounts.pricePerLiterMilliCents != null
        val discountRead = amounts.discountCents > 0
        // Só os valores lidos contam como "escritos"; o terceiro deriva do total pela regra habitual.
        if (volumeRead) touched += AmountField.VOLUME
        if (priceRead) touched += AmountField.PRICE
        _uiState.update {
            it.copy(
                volumeText = if (volumeRead) NumberInput.formatScaled(amounts.volumeMl!!.toLong(), 3, trimTrailingZeros = true) else it.volumeText,
                priceText = if (priceRead) NumberInput.formatScaled(amounts.pricePerLiterMilliCents!!.toLong(), 5, trimTrailingZeros = true) else it.priceText,
                discountText = if (discountRead) NumberInput.formatScaled(amounts.discountCents, 2) else it.discountText,
                ocrDiscountCents = amounts.discountCents.takeIf { discountRead },
                ocrOutcome = when {
                    volumeRead && priceRead -> OcrOutcome.VOLUME_AND_PRICE
                    volumeRead -> OcrOutcome.VOLUME_ONLY
                    priceRead -> OcrOutcome.PRICE_ONLY
                    else -> OcrOutcome.NOTHING
                },
            )
        }
        deriveMissingAmount()
        revalidate()
    }

    fun onDismissScanError() {
        _uiState.update { it.copy(scanFailed = false) }
    }

    /** O ecrã abriu a leitura do talão pedida pelo fluxo rápido. */
    fun onReceiptScanStarted() {
        _uiState.update { it.copy(pendingReceiptScan = false) }
    }

    /** O ecrã abriu a leitura do conta-km sugerida depois do talão. */
    fun onOdometerScanStarted() {
        _uiState.update { it.copy(pendingOdometerScan = false) }
    }

    /** Valor lido pela câmara; é uma sugestão como qualquer outra e continua editável. */
    fun onOdometerScanned(km: Int) = edit { copy(odometerText = km.toString(), pendingOdometerScan = false) }

    /** O utilizador preferiu escrever: pré-preenche com o valor previsto se o campo estiver vazio. */
    fun onOdometerManualRequested() {
        val state = _uiState.value
        val predicted = state.predictedOdometerKm
        if (state.odometerText.isBlank() && predicted != null) {
            edit { copy(odometerText = predicted.toString(), pendingOdometerScan = false) }
        } else {
            _uiState.update { it.copy(pendingOdometerScan = false) }
        }
    }

    private fun applyReceipt(code: AtQrCode) {
        val receipt = ScannedReceipt(
            issuerNif = code.issuerNif,
            buyerNif = code.buyerNif,
            atcud = code.atcud,
            documentId = code.documentId,
            documentDate = code.documentDate,
            totalCents = code.totalCents,
        )
        // O total passa a vir do talão. Litros e preço já escritos (à mão ou por uma leitura anterior)
        // continuam a contar como escritos; uma nova leitura por OCR substitui-os se ler valores.
        val current = _uiState.value
        touched.clear()
        if (current.volumeText.isNotBlank()) touched += AmountField.VOLUME
        if (current.priceText.isNotBlank()) touched += AmountField.PRICE
        touched += AmountField.TOTAL
        _uiState.update {
            it.copy(
                scanFailed = false,
                receipt = receipt,
                receiptTextOnly = false,
                ocrOutcome = null,
                date = code.documentDate,
                totalText = NumberInput.formatScaled(code.totalCents, 2),
                // Passo seguinte do fluxo: ler o painel, a não ser que o odómetro já esteja escrito.
                pendingOdometerScan = it.odometerText.isBlank(),
            )
        }
        viewModelScope.launch {
            atcudAlreadyUsed = repository.isAtcudUsed(code.atcud, excludeId = fillUpId)
            revalidate()
        }
    }

    private fun onAmountChange(field: AmountField, text: String) {
        touched += field
        _uiState.update {
            when (field) {
                AmountField.VOLUME -> it.copy(volumeText = text)
                AmountField.PRICE -> it.copy(priceText = text)
                AmountField.TOTAL -> it.copy(totalText = text)
            }
        }
        deriveMissingAmount()
        revalidate()
    }

    /**
     * Com exatamente dois dos três valores editados pelo utilizador, calcula o terceiro.
     * O desconto entra sempre na conta: litros × preço = total + desconto.
     */
    private fun deriveMissingAmount() {
        if (touched.size != 2) return
        val state = _uiState.value
        val volume = NumberInput.parseLitersToMl(state.volumeText)
        val price = NumberInput.parsePricePerLiterToMilliCents(state.priceText)
        val total = NumberInput.parseEurosToCents(state.totalText)
        val discount = parseDiscount(state.discountText)?.takeIf { it >= 0 } ?: 0L
        val gross = total?.plus(discount)
        val missing = AmountField.entries.first { it !in touched }
        val derived = when (missing) {
            AmountField.TOTAL -> {
                val derivedTotal = if (volume != null && price != null && volume > 0 && price > 0) FuelMath.totalCents(volume, price) - discount else null
                derivedTotal?.takeIf { it > 0 }?.let { NumberInput.formatScaled(it, 2) }.orEmpty()
            }
            AmountField.PRICE ->
                if (volume != null && gross != null && volume > 0 && gross > 0) {
                    // Os talões mostram o preço com 3 casas decimais: arredonda a múltiplos de 100 milésimos de cêntimo.
                    val price = FuelMath.roundDiv(FuelMath.pricePerLiterMilliCents(gross, volume).toLong(), 100) * 100
                    NumberInput.formatScaled(price, 5, trimTrailingZeros = true)
                } else ""
            AmountField.VOLUME ->
                if (price != null && gross != null && price > 0 && gross > 0) NumberInput.formatScaled(FuelMath.volumeMl(gross, price).toLong(), 3, trimTrailingZeros = true) else ""
        }
        _uiState.update {
            when (missing) {
                AmountField.TOTAL -> it.copy(totalText = derived)
                AmountField.PRICE -> it.copy(priceText = derived)
                AmountField.VOLUME -> it.copy(volumeText = derived)
            }
        }
    }

    /** Vazio = sem desconto; null = texto inválido. */
    private fun parseDiscount(text: String): Long? = if (text.isBlank()) 0L else NumberInput.parseEurosToCents(text)

    private fun edit(transform: FillUpUiState.() -> FillUpUiState) {
        _uiState.update { it.transform() }
        revalidate()
    }

    private fun currentDraft(state: FillUpUiState = _uiState.value): FillUpDraft = FillUpDraft(
        id = fillUpId,
        dateTime = LocalDateTime.of(state.date, state.time).atZone(clock.zone).toInstant(),
        odometerKm = NumberInput.parseOdometerKm(state.odometerText),
        volumeMl = NumberInput.parseLitersToMl(state.volumeText),
        pricePerLiterMilliCents = NumberInput.parsePricePerLiterToMilliCents(state.priceText),
        totalCents = NumberInput.parseEurosToCents(state.totalText),
        discountCents = parseDiscount(state.discountText),
        isFullTank = state.isFullTank,
        atcud = state.receipt?.atcud,
        buyerNif = state.receipt?.buyerNif,
    )

    private fun revalidate() {
        val state = _uiState.value
        val draft = currentDraft(state)
        val issues = FillUpValidator.validate(
            draft = draft,
            existing = existing,
            tankCapacityMl = tankCapacityMl,
            atcudAlreadyUsed = atcudAlreadyUsed,
            userNif = userNif,
        )
        val others = existing.filter { it.id != fillUpId }
        val previous = FillUpValidator.previousFillUp(others, draft.dateTime)
        val predicted = ConsumptionCalculator.predictNextOdometerKm(others)
        _uiState.update {
            it.copy(
                issues = issues,
                previousOdometerKm = previous?.odometerKm,
                predictedOdometerKm = predicted,
                askWarningConfirmation = false,
            )
        }
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        if (state.issues.hasErrors) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        if (state.issues.hasWarnings && !state.askWarningConfirmation) {
            _uiState.update { it.copy(askWarningConfirmation = true) }
            return
        }
        persist()
    }

    fun onConfirmWarnings() = persist()

    fun onDismissWarnings() {
        _uiState.update { it.copy(askWarningConfirmation = false) }
    }

    private fun persist() {
        val state = _uiState.value
        val draft = currentDraft(state)
        val odometer = draft.odometerKm ?: return
        val volume = draft.volumeMl ?: return
        val price = draft.pricePerLiterMilliCents ?: return
        val total = draft.totalCents ?: return
        val discount = draft.discountCents ?: return

        _uiState.update { it.copy(isSaving = true, askWarningConfirmation = false) }
        viewModelScope.launch {
            val base = original
            val receipt = state.receipt
            val fillUp = FillUp(
                id = fillUpId,
                dateTime = draft.dateTime,
                odometerKm = odometer,
                volumeMl = volume,
                pricePerLiterMilliCents = price,
                totalCents = total,
                discountCents = discount,
                isFullTank = state.isFullTank,
                stationNif = receipt?.issuerNif,
                stationName = state.stationName.trim().ifEmpty { null },
                // O ATCUD é único na base de dados: um talão repetido guarda-se sem a ligação ao ATCUD.
                atcud = receipt?.atcud?.takeUnless { atcudAlreadyUsed },
                buyerNifPresent = receipt?.let { !it.buyerIsFinalConsumer },
                notes = state.notes.trim().ifEmpty { null },
                createdAt = base?.createdAt ?: clock.instant(),
            )
            if (base == null) repository.insert(fillUp) else repository.update(fillUp)
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    private val ScannedReceipt.buyerIsFinalConsumer: Boolean get() = buyerNif == Nif.FINAL_CONSUMER

    /** Reconstrói o que se sabe do talão de um abastecimento guardado (sem o texto do QR). */
    private fun FillUp.toStoredReceipt(): ScannedReceipt? {
        val atcud = atcud ?: return null
        val issuer = stationNif ?: return null
        return ScannedReceipt(
            issuerNif = issuer,
            buyerNif = if (buyerNifPresent == true) (userNif ?: "") else Nif.FINAL_CONSUMER,
            atcud = atcud,
            documentId = null,
            documentDate = LocalDateTime.ofInstant(dateTime, clock.zone).toLocalDate(),
            totalCents = totalCents,
        )
    }
}
