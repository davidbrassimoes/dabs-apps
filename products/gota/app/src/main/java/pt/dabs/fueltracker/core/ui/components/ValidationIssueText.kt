package pt.dabs.fueltracker.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.domain.NumberInput
import pt.dabs.fueltracker.core.domain.Severity
import pt.dabs.fueltracker.core.domain.ValidationIssue
import pt.dabs.fueltracker.core.ui.format.PtFormat

@Composable
fun ValidationIssue.message(): String = when (this) {
    ValidationIssue.MissingOdometer -> stringResource(R.string.validation_missing_odometer)
    ValidationIssue.MissingVolume -> stringResource(R.string.validation_missing_volume)
    ValidationIssue.MissingPrice -> stringResource(R.string.validation_missing_price)
    ValidationIssue.MissingTotal -> stringResource(R.string.validation_missing_total)
    ValidationIssue.InvalidDiscount -> stringResource(R.string.validation_invalid_discount)
    ValidationIssue.InvalidTrip -> stringResource(R.string.validation_invalid_trip)
    is ValidationIssue.TripMismatch -> stringResource(
        if (tripTenthsKm < odometerDistanceKm * 10L) R.string.validation_trip_shorter else R.string.validation_trip_longer,
        NumberInput.formatScaled(tripTenthsKm.toLong(), 1) + " km",
        PtFormat.km(odometerDistanceKm),
    )
    is ValidationIssue.OdometerNotAfterPrevious ->
        stringResource(R.string.validation_odometer_not_after_previous, PtFormat.km(previousOdometerKm))
    is ValidationIssue.OdometerNotBeforeNext ->
        stringResource(R.string.validation_odometer_not_before_next, PtFormat.km(nextOdometerKm))
    is ValidationIssue.ImplausibleConsumption ->
        stringResource(R.string.validation_implausible_consumption, PtFormat.consumption(consumptionMlPer100Km))
    is ValidationIssue.TotalMismatch ->
        stringResource(R.string.validation_total_mismatch, PtFormat.euros(expectedTotalCents))
    ValidationIssue.DuplicateAtcud -> stringResource(R.string.validation_duplicate_atcud)
}

@Composable
fun ValidationIssueText(issue: ValidationIssue, modifier: Modifier = Modifier) {
    val isError = issue.severity == Severity.ERROR
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isError) Icons.Default.Info else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = issue.message(), color = color, style = MaterialTheme.typography.bodySmall)
    }
}
