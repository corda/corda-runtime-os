package net.corda.ledger.persistence.query

import net.corda.v5.ledger.common.query.VaultNamedQuery
import net.corda.v5.ledger.common.query.VaultNamedQueryCollector
import net.corda.v5.ledger.common.query.VaultNamedQueryFilter
import net.corda.v5.ledger.common.query.VaultNamedQueryRegistry
import net.corda.v5.ledger.common.query.VaultNamedQueryTransformer
import net.corda.v5.ledger.utxo.ContractState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class VaultNamedQueryBuilderFactoryImplTest {

    private companion object {
        const val DUMMY_QUERY_NAME = "dummy"
        const val DUMMY_WHERE_CLAUSE = "WHERE custom ->> 'TestUtxoState.testField' = :testField"
    }

    private val storedQueries = mutableListOf<VaultNamedQuery>()
    private val mockRegistry = mock<VaultNamedQueryRegistry> {
        on { registerQuery(any()) } doAnswer {
            storedQueries.add(it.arguments.first() as VaultNamedQuery)
            Unit
        }
    }

    @BeforeEach
    fun clear() {
        storedQueries.clear()
    }

    @Test
    fun `builder will register a query with name, where clause, filter, collector and mapper`() {
        val mockFilter = mock<VaultNamedQueryFilter<ContractState>>()
        val mockMapper = mock<VaultNamedQueryTransformer<ContractState, DummyPojo>>()
        val mockCollector = mock<VaultNamedQueryCollector<DummyPojo, Int>>()

        VaultNamedQueryBuilderFactoryImpl(mockRegistry)
            .create(DUMMY_QUERY_NAME)
            .whereJson(DUMMY_WHERE_CLAUSE)
            .collect(mockCollector)
            .map(mockMapper)
            .filter(mockFilter)
            .register()

        assertThat(storedQueries).hasSize(1)

        val storedQuery = storedQueries.first()

        assertThat(storedQuery).isNotNull
        assertThat(storedQuery.name).isEqualTo(DUMMY_QUERY_NAME)
        assertThat(storedQuery.jsonString).isEqualTo(DUMMY_WHERE_CLAUSE)
        assertThat(storedQuery.filter).isNotNull
        assertThat(storedQuery.mapper).isNotNull
        assertThat(storedQuery.collector).isNotNull
    }

    @Test
    fun `builder will register a query that only has a name`() {
        VaultNamedQueryBuilderFactoryImpl(mockRegistry)
            .create(DUMMY_QUERY_NAME)
            .register()

        assertThat(storedQueries).hasSize(1)

        val storedQuery = storedQueries.first()

        assertThat(storedQuery).isNotNull
        assertThat(storedQuery.name).isEqualTo(DUMMY_QUERY_NAME)
        assertThat(storedQuery.jsonString).isNull()
        assertThat(storedQuery.filter).isNull()
        assertThat(storedQuery.mapper).isNull()
        assertThat(storedQuery.collector).isNull()
    }

    @Test
    fun `builder will throw exception if the query has no name`() {
        val ex = assertThrows<IllegalArgumentException> {
            VaultNamedQueryBuilderFactoryImpl(mockRegistry).register()
        }

        assertThat(ex.message).contains("Named ledger query can't be registered without a name.")
        assertThat(storedQueries).isEmpty()
    }

    private class DummyPojo
}
