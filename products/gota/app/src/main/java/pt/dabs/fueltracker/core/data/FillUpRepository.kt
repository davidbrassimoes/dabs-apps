package pt.dabs.fueltracker.core.data

import kotlinx.coroutines.flow.Flow
import pt.dabs.fueltracker.core.domain.model.FillUp

interface FillUpRepository {
    /** Todos os abastecimentos em ordem cronológica ascendente. */
    fun observeAll(): Flow<List<FillUp>>

    suspend fun getById(id: Long): FillUp?

    /** Insere e devolve o id. Se [fillUp] já tiver id, é mantido (reposição após apagar). */
    suspend fun insert(fillUp: FillUp): Long

    suspend fun update(fillUp: FillUp)

    suspend fun delete(fillUp: FillUp)

    /** true se outro abastecimento (id ≠ [excludeId]) já tem este ATCUD. */
    suspend fun isAtcudUsed(atcud: String, excludeId: Long): Boolean
}
