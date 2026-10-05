package pt.dabs.fueltracker.feature.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** Etiquetas do eixo horizontal (uma por ponto), guardadas no modelo do gráfico. */
private val LabelsKey = ExtraStore.Key<List<String>>()

private val LabelFormatter = object : CartesianValueFormatter {
    override fun format(context: CartesianMeasuringContext, value: Double, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
        context.model.extraStore.getOrNull(LabelsKey)?.getOrNull(value.toInt()) ?: ""
}

/** Tema do Vico derivado do tema Material 3 da app (claro, escuro e dynamic color). */
@Composable
fun ProvideFuelChartTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val theme = remember(scheme) {
        VicoTheme(
            candlestickCartesianLayerColors = VicoTheme.CandlestickCartesianLayerColors(scheme.primary, scheme.outline, scheme.error),
            columnCartesianLayerColors = listOf(scheme.primary, scheme.tertiary, scheme.secondary),
            lineColor = scheme.outlineVariant,
            textColor = scheme.onSurfaceVariant,
        )
    }
    ProvideVicoTheme(theme, content)
}

/**
 * Gráfico de linha com um ponto por item. [values] já em unidades de apresentação (ex.: L/100 km).
 */
@Composable
fun FuelLineChart(
    labels: List<String>,
    values: List<Double>,
    valueSuffix: String,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(labels, values) {
        if (values.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineModel { series(values) }
            extras { it[LabelsKey] = labels }
        }
    }
    val startFormatter = remember(decimals, valueSuffix) {
        CartesianValueFormatter.decimal(decimalCount = decimals, decimalSeparator = ",", suffix = valueSuffix)
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(color)),
                        areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
                        ),
                    )
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = LabelFormatter, labelRotationDegrees = 45f, guideline = null),
            marker = rememberFuelMarker(valueSuffix),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(240.dp),
        scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
    )
}

/** Gráfico de colunas com uma coluna por item. */
@Composable
fun FuelColumnChart(
    labels: List<String>,
    values: List<Double>,
    valueSuffix: String,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(labels, values) {
        if (values.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            columnModel { series(values) }
            extras { it[LabelsKey] = labels }
        }
    }
    val startFormatter = remember(decimals, valueSuffix) {
        CartesianValueFormatter.decimal(decimalCount = decimals, decimalSeparator = ",", suffix = valueSuffix)
    }
    val column = rememberLineComponent(
        fill = Fill(color),
        thickness = 14.dp,
        shape = RoundedCornerShape(topStartPercent = 30, topEndPercent = 30),
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
                columnCollectionSpacing = 12.dp,
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = LabelFormatter, labelRotationDegrees = 45f, guideline = null),
            marker = rememberFuelMarker(valueSuffix),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(240.dp),
        scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
    )
}

@Composable
private fun rememberFuelMarker(valueSuffix: String): DefaultCartesianMarker {
    val scheme = MaterialTheme.colorScheme
    val background = rememberShapeComponent(
        fill = Fill(scheme.surfaceContainerHigh),
        shape = RoundedCornerShape(8.dp),
        strokeFill = Fill(scheme.outline),
        strokeThickness = 1.dp,
    )
    val label = rememberTextComponent(
        style = TextStyle(color = scheme.onSurface, fontSize = 12.sp),
        padding = Insets(8.dp, 4.dp),
        background = background,
    )
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = DefaultCartesianMarker.ValueFormatter.default(suffix = valueSuffix),
    )
}
