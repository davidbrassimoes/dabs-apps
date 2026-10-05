package pt.dabs.fueltracker.core.data

/** Pede a atualização dos widgets depois de uma alteração aos dados. Implementado na camada de widget. */
fun interface WidgetRefresher {
    suspend fun refresh()
}
