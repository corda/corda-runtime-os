package net.corda.ledger.consensual.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.consensual.flow.impl.persistence.external.events.AbstractConsensualLedgerExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
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

class ConsensualLedgerPersistenceServiceImplTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytes<Any>(byteBuffer.array())
    }

    private val serializationService = mock<SerializationService>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()

    private lateinit var consensualLedgerPersistenceService: ConsensualLedgerPersistenceService

    private val argumentCaptor = argumentCaptor<Class<out AbstractConsensualLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        consensualLedgerPersistenceService =
            ConsensualLedgerPersistenceServiceImpl(externalEventExecutor, serializationService)

        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(listOf(byteBuffer))
    }

    @Test
    fun `persist executes successfully`() {
        consensualLedgerPersistenceService.persist(mock())

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val expectedObj = mock<ConsensualSignedTransaction>()
        val testId = SecureHash.parse("SHA256:1234567890123456")
        whenever(serializationService.deserialize<ConsensualSignedTransaction>(any<ByteArray>(), any())).thenReturn(expectedObj)

        assertThat(consensualLedgerPersistenceService.find(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<ConsensualSignedTransaction>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }
}