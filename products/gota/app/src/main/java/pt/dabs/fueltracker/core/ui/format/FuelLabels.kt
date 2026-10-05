package pt.dabs.fueltracker.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.domain.model.FuelBrand
import pt.dabs.fueltracker.core.domain.model.FuelType

/** Nome da marca para mostrar ("Outra" vem dos recursos). */
@Composable
fun FuelBrand.label(): String = brandName ?: stringResource(R.string.brand_other)

/** Nome curto do combustível, para botões ("95", "Gasóleo"). */
@Composable
fun FuelType.shortLabel(): String = stringResource(
    when (this) {
        FuelType.GASOLINE_95 -> R.string.fuel_95_short
        FuelType.GASOLINE_98 -> R.string.fuel_98_short
        FuelType.DIESEL -> R.string.fuel_diesel
        FuelType.LPG -> R.string.fuel_lpg
    }
)

/** Combustível por extenso, com "aditivado/a" quando se sabe ("Gasolina 95 aditivada"). */
@Composable
fun fuelLabel(type: FuelType?, premium: Boolean?): String? {
    val base = when (type) {
        FuelType.GASOLINE_95 -> stringResource(R.string.fuel_95)
        FuelType.GASOLINE_98 -> stringResource(R.string.fuel_98)
        FuelType.DIESEL -> stringResource(R.string.fuel_diesel)
        FuelType.LPG -> stringResource(R.string.fuel_lpg)
        null -> null
    }
    // "Gasolina" e "GPL" pedem o feminino/masculino certos: o sufixo depende do tipo.
    val suffix = when (premium) {
        true -> stringResource(if (type == FuelType.DIESEL || type == FuelType.LPG) R.string.fuel_premium_m else R.string.fuel_premium_f)
        false -> stringResource(if (type == FuelType.DIESEL || type == FuelType.LPG) R.string.fuel_simple_m else R.string.fuel_simple_f)
        null -> null
    }
    return when {
        base != null && suffix != null -> "$base $suffix"
        base != null -> base
        suffix != null -> suffix.replaceFirstChar { it.uppercase() }
        else -> null
    }
}
