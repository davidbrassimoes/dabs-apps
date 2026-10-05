package pt.dabs.fueltracker.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val NIF = stringPreferencesKey("nif")
    }

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            nif = prefs[Keys.NIF]?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun setNif(nif: String?) {
        dataStore.edit { prefs ->
            if (nif.isNullOrBlank()) prefs.remove(Keys.NIF) else prefs[Keys.NIF] = nif
        }
    }
}
