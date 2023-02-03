package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.TransactionVerificationResult
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationExternalEventFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class UtxoLedgerTransactionVerificationServiceImplTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytes<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private lateinit var verificationService: UtxoLedgerTransactionVerificationServiceImpl
    private val argumentCaptor = argumentCaptor<Class<TransactionVerificationExternalEventFactory>>()

    @BeforeEach
    fun setup() {
        verificationService = UtxoLedgerTransactionVerificationServiceImpl(
            externalEventExecutor,
            serializationService
        )

        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
    }

    @Test
    fun `verification of valid transaction`() {
        val expectedObj = TransactionVerificationResult(
            TransactionVerificationStatus.VERIFIED,
            errorType = null,
            errorMessage = null
        )
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(expectedObj)

        val transaction = mock<UtxoLedgerTransactionInternal>()
        val wireTransaction = mock<WireTransaction>()
        val transactionMetadata = mock<TransactionMetadata>()
        whenever(transaction.wireTransaction).thenReturn(wireTransaction)
        whenever(wireTransaction.metadata).thenReturn(transactionMetadata)
        whenever(transactionMetadata.getCpiMetadata()).thenReturn(mock())

        assertDoesNotThrow {
            verificationService.verify(transaction)
        }

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(TransactionVerificationExternalEventFactory::class.java)
    }

    @Test
    fun `verification of invalid transaction results with exception`() {
        val expectedObj = TransactionVerificationResult(
            TransactionVerificationStatus.INVALID,
            errorType = "net.corda.v5.ledger.utxo.ContractVerificationException",
            errorMessage = "Contract verification failed"
        )
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(expectedObj)

        val transactionId = mock<SecureHash>()
        val transaction = mock<UtxoLedgerTransactionInternal>()
        val wireTransaction = mock<WireTransaction>()
        val transactionMetadata = mock<TransactionMetadata>()
        whenever(transaction.id).thenReturn(transactionId)
        whenever(transaction.wireTransaction).thenReturn(wireTransaction)
        whenever(wireTransaction.metadata).thenReturn(transactionMetadata)
        whenever(transactionMetadata.getCpiMetadata()).thenReturn(mock())

        val exception = assertThrows<TransactionVerificationException> {
            verificationService.verify(transaction)
        }

        assertThat(exception.transactionId).isEqualTo(transactionId)
        assertThat(exception.status).isEqualTo(expectedObj.status)
        assertThat(exception.originalExceptionClassName).isEqualTo(expectedObj.errorType)
        assertThat(exception.status).isEqualTo(expectedObj.status)

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(TransactionVerificationExternalEventFactory::class.java)
    }
}
