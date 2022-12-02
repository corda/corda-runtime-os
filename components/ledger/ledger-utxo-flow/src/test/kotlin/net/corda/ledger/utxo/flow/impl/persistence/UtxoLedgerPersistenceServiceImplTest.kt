package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.external.events.AbstractUtxoLedgerExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
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

class UtxoLedgerPersistenceServiceImplTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytes<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private val transactionSignatureService = mock<TransactionSignatureService>()

    private lateinit var UtxoLedgerPersistenceService: UtxoLedgerPersistenceService

    private val argumentCaptor = argumentCaptor<Class<out AbstractUtxoLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        UtxoLedgerPersistenceService = UtxoLedgerPersistenceServiceImpl(
                externalEventExecutor, serializationService, transactionSignatureService
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
        val expectedObj = mock<CordaPackageSummaryImpl>()
        whenever(serializationService.deserialize<CordaPackageSummaryImpl>(any<ByteArray>(), any())).thenReturn(expectedObj)
        val transaction = mock<UtxoSignedTransactionInternal>()
        whenever(transaction.wireTransaction).thenReturn(mock())
        whenever(transaction.signatures).thenReturn(mock())

        assertThat(
            UtxoLedgerPersistenceService.persist(
                transaction,
                TransactionStatus.VERIFIED
            )
        ).isEqualTo(listOf(expectedObj))

        verify(serializationService).serialize(any())
        verify(serializationService).deserialize<CordaPackageSummaryImpl>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `find executes successfully`() {
        val wireTransaction = mock<WireTransaction>()
        val signatures = listOf(mock<DigitalSignatureAndMetadata>())
        val expectedObj = UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signatures
        )
        val testId = SecureHash.parse("SHA256:1234567890123456")
        whenever(serializationService.deserialize<SignedTransactionContainer>(any<ByteArray>(), any()))
            .thenReturn(SignedTransactionContainer(wireTransaction, signatures))

        assertThat(UtxoLedgerPersistenceService.find(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<UtxoSignedTransactionInternal>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }
}
