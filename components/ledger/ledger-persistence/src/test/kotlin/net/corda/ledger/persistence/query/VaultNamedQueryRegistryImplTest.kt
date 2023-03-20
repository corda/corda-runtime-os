package net.corda.ledger.persistence.query

import net.corda.ledger.persistence.query.impl.VaultNamedQuery
import net.corda.ledger.persistence.query.impl.VaultNamedQueryRegistryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class VaultNamedQueryRegistryImplTest {

    private companion object {
        const val DUMMY_QUERY_NAME = "DUMMY"
        const val DUMMY_JSON_QUERY = "WHERE custom ->> 'TestUtxoState.testField' = :testField"
    }

    private val mockNamedQuery = mock<VaultNamedQuery> {
        on { name } doReturn DUMMY_QUERY_NAME
        on { jsonString } doReturn DUMMY_JSON_QUERY
    }

    @Test
    fun `registry can store named queries and those queries can be fetched from the registry`() {
        val registry = VaultNamedQueryRegistryImpl()

        registry.registerQuery(mockNamedQuery)

        val storedNamedQuery = registry.getQuery(DUMMY_QUERY_NAME)

        assertThat(storedNamedQuery).isNotNull
        assertThat(storedNamedQuery?.name).isNotNull
        assertThat(storedNamedQuery?.name).isEqualTo(DUMMY_QUERY_NAME)
        assertThat(storedNamedQuery?.jsonString).isEqualTo(DUMMY_JSON_QUERY)
    }

    @Test
    fun `registry will return null if a query is not found in registry`() {
        val registry = VaultNamedQueryRegistryImpl()

        val storedNamedQuery = registry.getQuery(DUMMY_QUERY_NAME)

        assertThat(storedNamedQuery).isNull()
    }

    @Test
    fun `registry will throw exception if trying to insert a query with an existing name`() {
        val registry = VaultNamedQueryRegistryImpl()

        registry.registerQuery(mockNamedQuery)

        val ex = assertThrows<IllegalArgumentException> {
            registry.registerQuery(mockNamedQuery)
        }

        assertThat(ex.message).contains("A query with name $DUMMY_QUERY_NAME is already registered.")
    }
}
