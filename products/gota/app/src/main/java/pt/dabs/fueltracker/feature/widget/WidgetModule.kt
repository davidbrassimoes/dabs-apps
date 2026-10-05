package pt.dabs.fueltracker.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pt.dabs.fueltracker.core.data.WidgetRefresher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun provideWidgetRefresher(@ApplicationContext context: Context): WidgetRefresher = WidgetRefresher {
        // Sem widgets colocados, updateAll não faz nada; falhas do launcher não devem afetar a gravação.
        runCatching { FuelWidget().updateAll(context) }
    }
}
