package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.flow.impl.persistence.external.events.VaultNamedQueryExternalEventFactory
import net.corda.utilities.days
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.query.VaultNamedParameterizedQuery
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.time.Instant

class VaultNamedParameterizedQueryImplTest {

    @Test
    fun `Vault named parameterized query cannot set the same parameter twice`() {
        val query = createQuery()

        query.setParameter("dummy", "dummy")

        val ex = assertThrows<IllegalArgumentException> {
            query.setParameter("dummy", "dummy")
        }

        assertThat(ex).hasStackTraceContaining("Parameter with key dummy is already set.")
    }

    @Test
    fun `Vault named parameterized query cannot set parameters from map if any of the parameters already set`() {
        val query = createQuery()

        query.setParameter("dummy", "dummy")

        val ex = assertThrows<IllegalArgumentException> {
            query.setParameters(mapOf("dummy" to "dummy", "dummy2" to "dummy2"))
        }

        assertThat(ex).hasStackTraceContaining("Parameters with keys: [dummy] are already set.")

        query.setParameter("dummy2", "dummy2")

        val ex2 = assertThrows<IllegalArgumentException> {
            query.setParameters(mapOf("dummy" to "dummy", "dummy2" to "dummy2"))
        }

        assertThat(ex2).hasStackTraceContaining("Parameters with keys: [dummy, dummy2] are already set.")
    }

    @Test
    fun `Vault named parameterized query cannot set offset twice`() {
        val query = createQuery()

        query.setOffset(100)

        val ex = assertThrows<IllegalArgumentException> {
            query.setOffset(100)
        }

        assertThat(ex).hasStackTraceContaining("Offset is already set.")
    }

    @Test
    fun `Vault named parameterized query cannot set timestamp limit to a future date`() {
        val query = createQuery()

        val ex = assertThrows<IllegalArgumentException> {
            query.setCreatedTimestampLimit(Instant.now().plusMillis(1.days.toMillis()))
        }

        assertThat(ex).hasStackTraceContaining("Timestamp limit must not be in the future.")
    }

    @Test
    fun `Vault named parameterized query cannot be executed without a valid offset`() {
        val query = createQuery()

        val ex = assertThrows<IllegalArgumentException> {
            query.execute()
        }

        assertThat(ex).hasStackTraceContaining(
            "Offset needs to be provided and needs to be a positive number to execute the query."
        )
    }

    @Test
    fun `Vault named parameterized query cannot be executed without a valid limit`() {
        val query = createQuery()
        query.setOffset(0)

        val ex = assertThrows<IllegalArgumentException> {
            query.execute()
        }

        assertThat(ex).hasStackTraceContaining(
            "Limit needs to be provided and needs to be a positive number to execute the query."
        )
    }

    @Test
    fun `Vault named parameterized query can map result from database properly`() {
        val query = createQuery()

        query.setOffset(0)
        query.setLimit(100)

        val resultSet = query.execute()

        assertThat(resultSet.results).hasSize(1)
        assertThat(resultSet.results.first()).isEqualTo("ABC")
    }

    private fun createQuery(): VaultNamedParameterizedQuery<String> {
        val dummyByteBuffer = ByteBuffer.wrap(ByteArray(0))

        val mockExternalEventExecutor = mock<ExternalEventExecutor> {
            on { execute(eq(VaultNamedQueryExternalEventFactory::class.java), any()) } doReturn listOf(dummyByteBuffer)
        }

        val mockSerializedBytes = mock<SerializedBytes<Any>> {
            on { bytes } doReturn ByteArray(0)
        }
        val mockSerializationService = mock<SerializationService> {
            on { deserialize(eq(dummyByteBuffer.array()), eq(String::class.java)) } doReturn "ABC"
            on { serialize<Any>(any()) } doReturn mockSerializedBytes
        }

        return VaultNamedParameterizedQueryImpl(
            "DUMMY",
            mockExternalEventExecutor,
            mockSerializationService,
            String::class.java
        )
    }
}