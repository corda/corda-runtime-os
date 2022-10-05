package net.corda.ledger.consensual.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.persistence.external.events.AbstractLedgerExternalEventFactory
import net.corda.ledger.consensual.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.consensual.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.consensual.persistence.internal.LedgerPersistenceClientImpl
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class LedgerPersistenceClientImplTest {

    private val serializationService = mock<SerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()

    private lateinit var ledgerPersistenceClient: LedgerPersistenceClient

    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())

    private val argumentCaptor = argumentCaptor<Class<out AbstractLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        ledgerPersistenceClient =
            LedgerPersistenceClientImpl(externalEventExecutor, serializationService)

        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
        whenever(serializedBytes.bytes).thenReturn(byteBuffer.array())
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(listOf(byteBuffer))
    }

    @Test
    fun `persist executes successfully`() {
        ledgerPersistenceClient.persist(mock<ConsensualSignedTransactionImpl>())

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val expectedObj = mock<ConsensualSignedTransactionImpl>()
        val testId = SecureHash.parse("SHA256:1234567890123456")
        whenever(serializationService.deserialize<ConsensualSignedTransactionImpl>(any<ByteArray>(), any())).thenReturn(expectedObj)

        assertThat(ledgerPersistenceClient.find(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<ConsensualSignedTransactionImpl>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }
}