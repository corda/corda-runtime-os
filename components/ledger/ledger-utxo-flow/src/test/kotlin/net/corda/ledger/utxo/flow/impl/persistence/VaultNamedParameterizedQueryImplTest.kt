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
    fun `Vault named parameterized query can set parameters multiple times`() {
        val query = createQuery()
        query.setParameter("dummy", "dummy")
        query.setParameter("dummy", "dummy1")
    }

    @Test
    fun `Vault named parameterized query can set parameters using map`() {
        val query = createQuery()
        query.setParameter("dummy", "dummy")
        query.setParameters(mapOf("dummy" to "dummy1"))
    }

    @Test
    fun `Vault named parameterized query can set offset multiple times`() {
        val query = createQuery()
        query.setOffset(100)
        query.setOffset(101)
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
            parameters = mutableMapOf(),
            limit = Int.MAX_VALUE,
            offset = 0,
            String::class.java
        )
    }
}