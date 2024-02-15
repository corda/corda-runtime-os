package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.ledger.persistence.query.registration.impl.VaultNamedQueryBuilderFactoryImpl
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VaultNamedQueryBuilderFactoryImplTest {

    private companion object {
        const val DUMMY_QUERY_NAME = "dummy"
        const val DUMMY_WHERE_CLAUSE_UNCONSUMED =
            "original AND (visible_states.consumed IS NULL OR visible_states.consumed >= :Corda_TimestampLimit)"
        const val DUMMY_WHERE_CLAUSE = "original"
        const val PARSED_WHERE_CLAUSE = "parsed"
        const val ORDER_BY_CLAUSE = "order original"
        const val ORDER_BY_PARSED = "order parsed"
    }

    private val storedQueries = mutableListOf<VaultNamedQuery>()
    private val mockRegistry = mock<VaultNamedQueryRegistry> {
        on { registerQuery(any()) } doAnswer {
            storedQueries.add(it.arguments.first() as VaultNamedQuery)
            Unit
        }
    }
    private val vaultNamedQueryParser = mock<VaultNamedQueryParser>()

    @BeforeEach
    fun clear() {
        storedQueries.clear()
    }

    @Test
    fun `builder will register a query with name, where clause, filter, collector and mapper`() {
        val mockFilter = mock<VaultNamedQueryFilter<ContractState>>()
        val mockMapper = mock<VaultNamedQueryTransformer<ContractState, DummyPojo>>()
        val mockCollector = mock<VaultNamedQueryCollector<DummyPojo, Int>>()

        whenever(vaultNamedQueryParser.parseWhereJson(DUMMY_WHERE_CLAUSE)).thenReturn(PARSED_WHERE_CLAUSE)

        VaultNamedQueryBuilderFactoryImpl(mockRegistry, vaultNamedQueryParser)
            .create(DUMMY_QUERY_NAME)
            .whereJson(DUMMY_WHERE_CLAUSE)
            .map(mockMapper)
            .filter(mockFilter)
            .collect(mockCollector)
            .register()

        assertThat(storedQueries).hasSize(1)

        val storedQuery = storedQueries.first()

        assertThat(storedQuery).isNotNull
        assertThat(storedQuery.name).isEqualTo(DUMMY_QUERY_NAME)
        assertThat(storedQuery.query).isEqualTo(
            VaultNamedQuery.ParsedQuery(
                DUMMY_WHERE_CLAUSE,
                PARSED_WHERE_CLAUSE,
                VaultNamedQuery.Type.WHERE_JSON
            )
        )
        assertThat(storedQuery.filter).isNotNull
        assertThat(storedQuery.mapper).isNotNull
        assertThat(storedQuery.collector).isNotNull
        assertThat(storedQuery.orderBy).isNull()
    }

    @Test
    fun `builder will register a query with name, where clause and extra clause for unconsumed only`() {
        whenever(vaultNamedQueryParser.parseWhereJson(DUMMY_WHERE_CLAUSE_UNCONSUMED)).thenReturn(PARSED_WHERE_CLAUSE)

        VaultNamedQueryBuilderFactoryImpl(mockRegistry, vaultNamedQueryParser)
            .create(DUMMY_QUERY_NAME)
            .whereJson(DUMMY_WHERE_CLAUSE)
            .selectUnconsumedStatesOnly()
            .register()

        assertThat(storedQueries).hasSize(1)

        val storedQuery = storedQueries.first()

        assertThat(storedQuery).isNotNull
        assertThat(storedQuery.name).isEqualTo(DUMMY_QUERY_NAME)
        assertThat(storedQuery.query).isEqualTo(
            VaultNamedQuery.ParsedQuery(
                DUMMY_WHERE_CLAUSE_UNCONSUMED,
                PARSED_WHERE_CLAUSE,
                VaultNamedQuery.Type.WHERE_JSON
            )
        )
        assertThat(storedQuery.filter).isNull()
        assertThat(storedQuery.mapper).isNull()
        assertThat(storedQuery.collector).isNull()
        assertThat(storedQuery.orderBy).isNull()
    }

    @Test
    fun `builder will register a query with name, where clause and order by clause`() {
        whenever(vaultNamedQueryParser.parseWhereJson(DUMMY_WHERE_CLAUSE)).thenReturn(PARSED_WHERE_CLAUSE)
        whenever(vaultNamedQueryParser.parseSimpleExpression(ORDER_BY_CLAUSE)).thenReturn(ORDER_BY_PARSED)

        VaultNamedQueryBuilderFactoryImpl(mockRegistry, vaultNamedQueryParser)
            .create(DUMMY_QUERY_NAME)
            .whereJson(DUMMY_WHERE_CLAUSE)
            .orderBy(ORDER_BY_CLAUSE, "DESC")
            .orderBy(ORDER_BY_CLAUSE)
            .register()

        assertThat(storedQueries).hasSize(1)

        val storedQuery = storedQueries.first()

        assertThat(storedQuery).isNotNull
        assertThat(storedQuery.name).isEqualTo(DUMMY_QUERY_NAME)
        assertThat(storedQuery.query).isEqualTo(
            VaultNamedQuery.ParsedQuery(
                DUMMY_WHERE_CLAUSE,
                PARSED_WHERE_CLAUSE,
                VaultNamedQuery.Type.WHERE_JSON
            )
        )
        assertThat(storedQuery.filter).isNull()
        assertThat(storedQuery.mapper).isNull()
        assertThat(storedQuery.collector).isNull()
        assertThat(storedQuery.orderBy).isEqualTo(
            VaultNamedQuery.ParsedQuery(
                "$ORDER_BY_CLAUSE DESC, $ORDER_BY_CLAUSE",
                "$ORDER_BY_PARSED DESC, $ORDER_BY_PARSED",
                VaultNamedQuery.Type.ORDER_BY
            )
        )
    }

    @Test
    fun `builder will not register a query that only has a name`() {
        assertThatThrownBy {
            VaultNamedQueryBuilderFactoryImpl(mockRegistry, vaultNamedQueryParser)
                .create(DUMMY_QUERY_NAME)
                .register()
        }.isExactlyInstanceOf(IllegalArgumentException::class.java)
    }

    private class DummyPojo
}
