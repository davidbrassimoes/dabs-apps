package pt.dabs.fueltracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class FuelTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // As definições antigas (NIF e capacidade do depósito) deixaram de existir: apaga o ficheiro
        // do DataStore onde ficaram guardadas, para o NIF não continuar no dispositivo.
        File(filesDir, "datastore/settings.preferences_pb").delete()
    }
}
