package net.corda.ledger.consensual.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.ledger.consensual.flow.impl.persistence.external.events.AbstractConsensualLedgerExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
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

class ConsensualLedgerPersistenceServiceImplTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytes<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private val signingService = mock<SigningService>()
    private val digitalSignatureVerificationService = mock<DigitalSignatureVerificationService>()

    private lateinit var consensualLedgerPersistenceService: ConsensualLedgerPersistenceService

    private val argumentCaptor = argumentCaptor<Class<out AbstractConsensualLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        consensualLedgerPersistenceService = ConsensualLedgerPersistenceServiceImpl(
                externalEventExecutor, serializationService, signingService, digitalSignatureVerificationService
        )

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
        val expectedObj = mock<CordaPackageSummary>()
        whenever(serializationService.deserialize<CordaPackageSummary>(any<ByteArray>(), any())).thenReturn(expectedObj)
        val transaction = mock<ConsensualSignedTransactionInternal>()
        whenever(transaction.wireTransaction).thenReturn(mock())
        whenever(transaction.signatures).thenReturn(mock())

        assertThat(consensualLedgerPersistenceService.persist(transaction, "V")).isEqualTo(listOf(expectedObj))

        verify(serializationService).serialize(any())
        verify(serializationService).deserialize<CordaPackageSummary>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val wireTransaction = mock<WireTransaction>()
        val signatures = listOf(mock<DigitalSignatureAndMetadata>())
        val expectedObj = ConsensualSignedTransactionImpl(
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            wireTransaction,
            signatures
        )
        val testId = SecureHash.parse("SHA256:1234567890123456")
        whenever(serializationService.deserialize<ConsensualSignedTransactionContainer>(any<ByteArray>(), any()))
            .thenReturn(ConsensualSignedTransactionContainer(wireTransaction, signatures))

        assertThat(consensualLedgerPersistenceService.find(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<ConsensualSignedTransactionInternal>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }
}