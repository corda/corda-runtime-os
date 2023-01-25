package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.ContractVerificationFailureImpl
import net.corda.ledger.utxo.data.transaction.ContractVerificationResult
import net.corda.ledger.utxo.data.transaction.ContractVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.VerifyContractsExternalEventFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.ContractVerificationException
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

class UtxoLedgerTransactionVerifierComponentTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytes<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private lateinit var utxoLedgerTransactionVerifier: UtxoLedgerTransactionVerifierComponent
    private val argumentCaptor = argumentCaptor<Class<VerifyContractsExternalEventFactory>>()

    @BeforeEach
    fun setup() {
        utxoLedgerTransactionVerifier = UtxoLedgerTransactionVerifierComponent(
            externalEventExecutor,
            serializationService
        )

        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
    }

    @Test
    fun `contracts verification executes successfully`() {
        val expectedObj = ContractVerificationResult(
            ContractVerificationStatus.VERIFIED,
            emptyList()
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
        whenever(transactionMetadata.getCpkMetadata()).thenReturn(listOf(mock()))

        assertDoesNotThrow {
            utxoLedgerTransactionVerifier.verifyContracts(transaction)
        }

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(VerifyContractsExternalEventFactory::class.java)
    }

    @Test
    fun `contracts verification executes unsuccessfully`() {
        val expectedObj = ContractVerificationResult(
            ContractVerificationStatus.INVALID,
            listOf(mock<ContractVerificationFailureImpl>())
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
        whenever(transactionMetadata.getCpkMetadata()).thenReturn(listOf(mock()))

        val exception = assertThrows<ContractVerificationException> {
            utxoLedgerTransactionVerifier.verifyContracts(transaction)
        }
        assertThat(exception.transactionId).isEqualTo(transactionId)
        assertThat(exception.failureReasons).isEqualTo(expectedObj.failureReasons)

        verify(serializationService).serialize(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(VerifyContractsExternalEventFactory::class.java)
    }
}
