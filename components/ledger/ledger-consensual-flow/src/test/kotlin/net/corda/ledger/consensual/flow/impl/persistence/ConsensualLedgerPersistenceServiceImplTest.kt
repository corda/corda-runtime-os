package net.corda.ledger.consensual.flow.impl.persistence

import net.corda.crypto.core.parseSecureHash
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.internal.serialization.SerializedBytesImpl
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.persistence.external.events.AbstractConsensualLedgerExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.common.transaction.TransactionMetadata
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
        private val serializedBytes = SerializedBytesImpl<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private val transactionSignatureService = mock<TransactionSignatureServiceInternal>()

    private lateinit var consensualLedgerPersistenceService: ConsensualLedgerPersistenceService

    private val argumentCaptor = argumentCaptor<Class<out AbstractConsensualLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        consensualLedgerPersistenceService = ConsensualLedgerPersistenceServiceImpl(
            externalEventExecutor, serializationService, transactionSignatureService
        )

        whenever(serializationService.serialize(any<Any>())).thenReturn(serializedBytes)
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(listOf(byteBuffer))
    }

    @Test
    fun `persist executes successfully`() {
        val expectedObj = mock<CordaPackageSummaryImpl>()
        whenever(serializationService.deserialize<CordaPackageSummaryImpl>(any<ByteArray>(), any())).thenReturn(expectedObj)
        val transaction = mock<ConsensualSignedTransactionInternal>()
        whenever(transaction.wireTransaction).thenReturn(mock())
        whenever(transaction.signatures).thenReturn(mock())

        assertThat(
            consensualLedgerPersistenceService.persist(
                transaction,
                TransactionStatus.VERIFIED
            )
        ).isEqualTo(listOf(expectedObj))

        verify(serializationService).serialize(any<Any>())
        verify(serializationService).deserialize<CordaPackageSummaryImpl>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val metadata = mock<TransactionMetadata>()
        whenever(metadata.getLedgerModel()).thenReturn(ConsensualLedgerTransactionImpl::class.java.name)
        val wireTransaction = mock<WireTransaction>()
        whenever(wireTransaction.metadata).thenReturn(metadata)

        val signatures = listOf(mock<DigitalSignatureAndMetadata>())
        val expectedObj = ConsensualSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signatures
        )
        val testId = parseSecureHash("SHA256:1234567890123456")
        whenever(serializationService.deserialize<SignedTransactionContainer>(any<ByteArray>(), any()))
            .thenReturn(SignedTransactionContainer(wireTransaction, signatures))

        assertThat(consensualLedgerPersistenceService.find(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<ConsensualSignedTransactionInternal>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }
}
