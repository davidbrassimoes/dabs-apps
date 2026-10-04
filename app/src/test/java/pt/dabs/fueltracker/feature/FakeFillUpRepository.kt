package pt.dabs.fueltracker.feature

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pt.dabs.fueltracker.core.data.FillUpRepository
import pt.dabs.fueltracker.core.domain.ConsumptionCalculator
import pt.dabs.fueltracker.core.domain.model.FillUp

/** Repositório em memória com o mesmo contrato do Room (ids autoincrementados, ordem cronológica). */
class FakeFillUpRepository(initial: List<FillUp> = emptyList()) : FillUpRepository {

    private val store = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    val current: List<FillUp> get() = store.value

    override fun observeAll(): Flow<List<FillUp>> = store.map { ConsumptionCalculator.sortChronologically(it) }

    override suspend fun getById(id: Long): FillUp? = store.value.firstOrNull { it.id == id }

    override suspend fun insert(fillUp: FillUp): Long {
        val id = if (fillUp.id == FillUp.NEW_ID) nextId++ else fillUp.id
        check(store.value.none { it.id == id }) { "duplicate id $id" }
        store.update { it + fillUp.copy(id = id) }
        return id
    }

    override suspend fun update(fillUp: FillUp) {
        store.update { list -> list.map { if (it.id == fillUp.id) fillUp else it } }
    }

    override suspend fun delete(fillUp: FillUp) {
        store.update { list -> list.filterNot { it.id == fillUp.id } }
    }

    override suspend fun isAtcudUsed(atcud: String, excludeId: Long): Boolean =
        store.value.any { it.atcud == atcud && it.id != excludeId }
}
