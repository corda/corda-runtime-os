package net.corda.flow.application.ledger

import net.corda.flow.application.ledger.external.events.AbstractLedgerExternalEventFactory
import net.corda.flow.application.ledger.external.events.FindTransactionExternalEventFactory
import net.corda.flow.application.ledger.external.events.PersistTransactionExternalEventFactory
import java.nio.ByteBuffer
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.impl.transaction.WireTransaction
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

class LedgerPersistenceServiceImplTest {

    private val serializationService = mock<SerializationService>()
    private val serializedBytes = mock<SerializedBytes<Any>>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()

    private lateinit var ledgerPersistenceService: LedgerPersistenceService

    private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())

    private val argumentCaptor = argumentCaptor<Class<out AbstractLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        ledgerPersistenceService =
            LedgerPersistenceServiceImpl(externalEventExecutor, serializationService)

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
        ledgerPersistenceService.persist(mock<WireTransaction>())

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val expectedObj = mock<WireTransaction>()
        val testId = SecureHash.parse("SHA256:1234567890123456")
        whenever(serializationService.deserialize<WireTransaction>(any<ByteArray>(), any())).thenReturn(expectedObj)

        assertThat(ledgerPersistenceService.find(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<WireTransaction>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }
}