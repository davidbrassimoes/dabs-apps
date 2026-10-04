package pt.dabs.fueltracker.feature.fillup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.domain.Severity
import pt.dabs.fueltracker.core.domain.ValidationIssue
import pt.dabs.fueltracker.core.ui.components.ValidationIssueText
import pt.dabs.fueltracker.core.ui.components.message
import pt.dabs.fueltracker.core.ui.format.PtFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun FillUpScreen(
    onDone: () -> Unit,
    onScanReceipt: () -> Unit,
    scannedQr: String?,
    onScannedQrConsumed: () -> Unit,
    viewModel: FillUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    LaunchedEffect(scannedQr) {
        if (scannedQr != null) {
            viewModel.onQrScanned(scannedQr)
            onScannedQrConsumed()
        }
    }

    FillUpContent(
        state = state,
        onBack = onDone,
        onScanReceipt = onScanReceipt,
        onDismissScanError = viewModel::onDismissScanError,
        onDateChange = viewModel::onDateChange,
        onTimeChange = viewModel::onTimeChange,
        onOdometerChange = viewModel::onOdometerChange,
        onVolumeChange = viewModel::onVolumeChange,
        onPriceChange = viewModel::onPriceChange,
        onTotalChange = viewModel::onTotalChange,
        onFullTankChange = viewModel::onFullTankChange,
        onStationNameChange = viewModel::onStationNameChange,
        onNotesChange = viewModel::onNotesChange,
        onSave = viewModel::onSaveClick,
        onConfirmWarnings = viewModel::onConfirmWarnings,
        onDismissWarnings = viewModel::onDismissWarnings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillUpContent(
    state: FillUpUiState,
    onBack: () -> Unit,
    onScanReceipt: () -> Unit,
    onDismissScanError: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onOdometerChange: (String) -> Unit,
    onVolumeChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onTotalChange: (String) -> Unit,
    onFullTankChange: (Boolean) -> Unit,
    onStationNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onConfirmWarnings: () -> Unit,
    onDismissWarnings: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val errors = state.issues.filter { it.severity == Severity.ERROR }
    val warnings = state.issues.filter { it.severity == Severity.WARNING }
    val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEdit) R.string.fillup_title_edit else R.string.fillup_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSave, enabled = !state.isLoading && !state.isSaving) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReceiptCard(receipt = state.receipt, onScanReceipt = onScanReceipt)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(state.date.format(dateFormatter))
                }
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(state.time.format(timeFormatter))
                }
            }

            OutlinedTextField(
                value = state.odometerText,
                onValueChange = onOdometerChange,
                label = { Text(stringResource(R.string.fillup_odometer)) },
                suffix = { Text("km") },
                supportingText = {
                    state.previousOdometerKm?.let { Text(stringResource(R.string.fillup_previous_odometer, PtFormat.km(it))) }
                },
                isError = state.showErrors && errors.any { it is ValidationIssue.MissingOdometer || it is ValidationIssue.OdometerNotAfterPrevious || it is ValidationIssue.OdometerNotBeforeNext },
                keyboardOptions = numberKeyboard,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.volumeText,
                    onValueChange = onVolumeChange,
                    label = { Text(stringResource(R.string.fillup_volume)) },
                    suffix = { Text("L") },
                    isError = state.showErrors && errors.any { it is ValidationIssue.MissingVolume },
                    keyboardOptions = decimalKeyboard,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = onPriceChange,
                    label = { Text(stringResource(R.string.fillup_price)) },
                    suffix = { Text("€/L") },
                    isError = state.showErrors && errors.any { it is ValidationIssue.MissingPrice },
                    keyboardOptions = decimalKeyboard,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.totalText,
                onValueChange = onTotalChange,
                label = { Text(stringResource(R.string.fillup_total)) },
                suffix = { Text("€") },
                isError = state.showErrors && errors.any { it is ValidationIssue.MissingTotal },
                keyboardOptions = decimalKeyboard,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.fillup_full_tank), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.fillup_full_tank_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.isFullTank, onCheckedChange = onFullTankChange)
            }

            OutlinedTextField(
                value = state.stationName,
                onValueChange = onStationNameChange,
                label = { Text(stringResource(R.string.fillup_station)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.fillup_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.showErrors) errors.forEach { ValidationIssueText(it) }
            warnings.forEach { ValidationIssueText(it) }

            Button(onClick = onSave, enabled = !state.isLoading && !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(initialHour = state.time.hour, initialMinute = state.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { TimePicker(state = pickerState) },
        )
    }

    if (state.scanFailed) {
        AlertDialog(
            onDismissRequest = onDismissScanError,
            title = { Text(stringResource(R.string.scan_failed_title)) },
            text = { Text(stringResource(R.string.scan_failed_body)) },
            confirmButton = {
                TextButton(onClick = { onDismissScanError(); onScanReceipt() }) { Text(stringResource(R.string.scan_retry)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissScanError) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    if (state.askWarningConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissWarnings,
            title = { Text(stringResource(R.string.fillup_warnings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    warnings.forEach { Text("• " + it.message()) }
                    Text(stringResource(R.string.fillup_warnings_question))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmWarnings) { Text(stringResource(R.string.fillup_save_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissWarnings) { Text(stringResource(R.string.action_review)) }
            },
        )
    }
}

@Composable
private fun ReceiptCard(receipt: ScannedReceipt?, onScanReceipt: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (receipt == null) {
                Text(stringResource(R.string.fillup_receipt_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.fillup_receipt_read), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        receipt.documentId,
                        receipt.documentDate.format(dateFormatter),
                        PtFormat.euros(receipt.totalCents),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.fillup_receipt_station_nif, receipt.issuerNif),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onScanReceipt, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (receipt == null) R.string.fillup_scan_receipt else R.string.fillup_scan_receipt_again))
            }
        }
    }
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtFormat.locale)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", PtFormat.locale)
