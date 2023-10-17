package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class VaultNamedQueryBuilderImplTest {

    private companion object {
        const val VALID_DUMMY_QUERY = "WHERE custom_representation ->> 'temp' = :testField"
    }

    private val dummyRegistry = mutableListOf<VaultNamedQuery>()

    private val mockVaultNamedQueryParser = mock<VaultNamedQueryParser> {
        // Just return the argument, do no parsing
        on { parseWhereJson(any()) } doAnswer { it.arguments.first() as String }
    }

    private val mockVaultNamedQueryRegistry = mock<VaultNamedQueryRegistry> {
        // Add it to a "registry"
        on { registerQuery(any()) } doAnswer {
            dummyRegistry.add(it.arguments.first() as VaultNamedQuery)
            Unit
        }
    }

    @BeforeEach
    fun clearRegistry() {
        dummyRegistry.clear()
    }

    @Test
    fun `vault named query builder cannot set filter twice`() {
        val builder = VaultNamedQueryBuilderImpl(mockVaultNamedQueryRegistry, mockVaultNamedQueryParser, "DUMMY")
            .whereJson(VALID_DUMMY_QUERY)
            .filter(DummyFilter())

        val ex = assertThrows<IllegalArgumentException> {
            builder.filter(DummyFilter())
        }

        assertThat(ex).hasStackTraceContaining("Filter function has already been set!")
    }

    @Test
    fun `vault named query builder cannot set mapper twice`() {
        val builder = VaultNamedQueryBuilderImpl(mockVaultNamedQueryRegistry, mockVaultNamedQueryParser, "DUMMY")
            .whereJson(VALID_DUMMY_QUERY)
            .map(DummyMapper())

        val ex = assertThrows<IllegalArgumentException> {
            builder.map(DummyMapper())
        }

        assertThat(ex).hasStackTraceContaining("Mapper function has already been set!")
    }

    @Test
    fun `vault named query builder cannot be collected without query`() {
        val ex = assertThrows<IllegalArgumentException> {
            VaultNamedQueryBuilderImpl(mockVaultNamedQueryRegistry, mockVaultNamedQueryParser, "DUMMY")
                .collect(DummyCollector())
        }

        assertThat(ex).hasStackTraceContaining("Vault named query: DUMMY does not have its query statement set")
    }

    @Test
    fun `vault named query builder cannot register without query`() {
        val ex = assertThrows<IllegalArgumentException> {
            VaultNamedQueryBuilderImpl(mockVaultNamedQueryRegistry, mockVaultNamedQueryParser, "DUMMY")
                .register()
        }

        assertThat(ex).hasStackTraceContaining("Vault named query: DUMMY does not have its query statement set")
    }

    @Test
    fun `vault named query builder can register if query set but no filter, mapper, collector is set`() {
        VaultNamedQueryBuilderImpl(mockVaultNamedQueryRegistry, mockVaultNamedQueryParser, "DUMMY")
            .whereJson(VALID_DUMMY_QUERY)
            .register()

        assertThat(dummyRegistry).hasSize(1)
        assertThat(dummyRegistry.first().name).isEqualTo("DUMMY")
        assertThat(dummyRegistry.first().query.query).isEqualTo(VALID_DUMMY_QUERY)
    }

    @Test
    fun `vault named query builder can register if query set and both filter, mapper, collector is set`() {
        VaultNamedQueryBuilderImpl(mockVaultNamedQueryRegistry, mockVaultNamedQueryParser, "DUMMY")
            .whereJson(VALID_DUMMY_QUERY)
            .filter(DummyFilter())
            .map(DummyMapper())
            .collect(DummyCollector())
            .register()

        assertThat(dummyRegistry).hasSize(1)
        assertThat(dummyRegistry.first().name).isEqualTo("DUMMY")
        assertThat(dummyRegistry.first().query.query).isEqualTo(VALID_DUMMY_QUERY)
    }

    private class DummyFilter : VaultNamedQueryFilter<String> {
        override fun filter(data: String, parameters: MutableMap<String, Any?>) = true
    }

    private class DummyMapper : VaultNamedQueryTransformer<Any, Any> {
        override fun transform(data: Any, parameters: MutableMap<String, Any?>): Any {
            return ""
        }
    }

    private class DummyCollector : VaultNamedQueryCollector<Any, Any> {
        override fun collect(
            resultSet: MutableList<Any>,
            parameters: MutableMap<String, Any?>
        ): VaultNamedQueryCollector.Result<Any> {
            return object : VaultNamedQueryCollector.Result<Any>(emptyList(), true) {}
        }
    }
}
