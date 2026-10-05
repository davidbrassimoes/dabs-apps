package pt.dabs.fueltracker.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.action.clickable
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import pt.dabs.fueltracker.MainActivity
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.data.FillUpRepository
import pt.dabs.fueltracker.core.domain.ConsumptionCalculator
import pt.dabs.fueltracker.core.ui.format.PtFormat

/** Widget do ecrã inicial: último consumo, média recente e atalho para registar um abastecimento. */
class FuelWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun fillUpRepository(): FillUpRepository
    }

    private data class WidgetData(
        val lastConsumptionMlPer100Km: Int?,
        val recentAverageMlPer100Km: Int?,
        val typicalDistanceKm: Int?,
        val fillUpCount: Int,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).fillUpRepository()
        val fillUps = repository.observeAll().first()
        val cycles = ConsumptionCalculator.computeCycles(fillUps)
        val data = WidgetData(
            lastConsumptionMlPer100Km = cycles.lastOrNull()?.consumptionMlPer100Km,
            recentAverageMlPer100Km = ConsumptionCalculator.recentAverageMlPer100Km(cycles),
            typicalDistanceKm = ConsumptionCalculator.estimateRangeKm(null, cycles)?.km,
            fillUpCount = fillUps.size,
        )
        provideContent {
            GlanceTheme {
                WidgetContent(context, data)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, data: WidgetData) {
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
        val newFillUp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply { action = MainActivity.ACTION_NEW_FILL_UP }
        )
        val dash = context.getString(R.string.no_value)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(openApp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = context.getString(R.string.widget_last_consumption),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                    Text(
                        text = data.lastConsumptionMlPer100Km?.let { PtFormat.consumption(it) } ?: dash,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = context.getString(R.string.widget_recent_average),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                    Text(
                        text = data.recentAverageMlPer100Km?.let { PtFormat.consumption(it) } ?: dash,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.typicalDistanceKm?.let { context.getString(R.string.widget_typical_distance, PtFormat.km(it)) }
                        ?: context.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = context.getString(R.string.home_add_fill_up),
                    style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(16.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clickable(newFillUp),
                )
            }
        }
    }
}

class FuelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FuelWidget()
}
