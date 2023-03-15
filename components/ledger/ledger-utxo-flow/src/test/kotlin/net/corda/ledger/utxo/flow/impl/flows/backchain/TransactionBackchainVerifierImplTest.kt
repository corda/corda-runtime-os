package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationException
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.testkit.getExampleInvalidStateAndRefImpl
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

@Suppress("MaxLineLength")
class TransactionBackchainVerifierImplTest {

    private companion object {
        val RESOLVING_TX_ID = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
        val TX_ID_1 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHashImpl("SHA", byteArrayOf(3, 3, 3, 3))
        val TX_ID_3 = SecureHashImpl("SHA", byteArrayOf(4, 4, 4, 4))
        val VERIFICATION_EXCEPTION = TransactionVerificationException(mock(), TransactionVerificationStatus.INVALID)
    }

    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val utxoLedgerTransactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()

    private val metadata = mock<TransactionMetadata>()

    private val transaction1 = mock<UtxoSignedTransactionInternal>()
    private val transaction2 = mock<UtxoSignedTransactionInternal>()
    private val transaction3 = mock<UtxoSignedTransactionInternal>()

    private val ledgerTransaction1 = mock<UtxoLedgerTransaction>()
    private val ledgerTransaction2 = mock<UtxoLedgerTransaction>()
    private val ledgerTransaction3 = mock<UtxoLedgerTransaction>()

    private val signatory = mock<PublicKey>()
    private val command = mock<Command>()

    private val transactionBackchainVerifier = TransactionBackchainVerifierImpl(
        utxoLedgerPersistenceService,
        utxoLedgerTransactionVerificationService
    )

    @BeforeEach
    fun beforeEach() {
        whenever(utxoLedgerPersistenceService.find(TX_ID_1, UNVERIFIED)).thenReturn(transaction1)
        whenever(utxoLedgerPersistenceService.find(TX_ID_2, UNVERIFIED)).thenReturn(transaction2)
        whenever(utxoLedgerPersistenceService.find(TX_ID_3, UNVERIFIED)).thenReturn(transaction3)
        whenever(transaction1.toLedgerTransaction()).thenReturn(ledgerTransaction1)
        whenever(transaction1.notary).thenReturn(utxoNotaryExample)
        whenever(transaction2.toLedgerTransaction()).thenReturn(ledgerTransaction2)
        whenever(transaction2.notary).thenReturn(utxoNotaryExample)
        whenever(transaction3.toLedgerTransaction()).thenReturn(ledgerTransaction3)
        whenever(transaction3.notary).thenReturn(utxoNotaryExample)
        whenever(ledgerTransaction1.id).thenReturn(TX_ID_1)
        whenever(ledgerTransaction1.inputStateRefs).thenReturn(listOf(getExampleStateAndRefImpl().ref))
        whenever(ledgerTransaction1.outputContractStates).thenReturn(emptyList())
        whenever(ledgerTransaction1.inputStateAndRefs).thenReturn(listOf(getExampleStateAndRefImpl()))
        whenever(ledgerTransaction1.outputStateAndRefs).thenReturn(emptyList())
        whenever(ledgerTransaction1.signatories).thenReturn(listOf(signatory))
        whenever(ledgerTransaction1.commands).thenReturn(listOf(command))
        whenever(ledgerTransaction1.metadata).thenReturn(metadata)
        whenever(ledgerTransaction2.id).thenReturn(TX_ID_2)
        whenever(ledgerTransaction2.inputStateRefs).thenReturn(listOf(getExampleStateAndRefImpl().ref))
        whenever(ledgerTransaction2.outputContractStates).thenReturn(emptyList())
        whenever(ledgerTransaction2.inputStateAndRefs).thenReturn(listOf(getExampleStateAndRefImpl()))
        whenever(ledgerTransaction2.outputStateAndRefs).thenReturn(emptyList())
        whenever(ledgerTransaction2.signatories).thenReturn(listOf(signatory))
        whenever(ledgerTransaction2.commands).thenReturn(listOf(command))
        whenever(ledgerTransaction2.metadata).thenReturn(metadata)
        whenever(ledgerTransaction3.id).thenReturn(TX_ID_3)
        whenever(ledgerTransaction3.inputStateRefs).thenReturn(listOf(getExampleStateAndRefImpl().ref))
        whenever(ledgerTransaction3.outputContractStates).thenReturn(emptyList())
        whenever(ledgerTransaction3.inputStateAndRefs).thenReturn(listOf(getExampleStateAndRefImpl()))
        whenever(ledgerTransaction3.outputStateAndRefs).thenReturn(emptyList())
        whenever(ledgerTransaction3.signatories).thenReturn(listOf(signatory))
        whenever(ledgerTransaction3.commands).thenReturn(listOf(command))
        whenever(ledgerTransaction3.metadata).thenReturn(metadata)
    }

    @Test
    fun `returns true when all transactions pass verification`() {
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isTrue
    }

    @Test
    fun `updates all transaction statuses when all transactions pass verification`() {
        transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())
        verify(utxoLedgerPersistenceService).updateStatus(TX_ID_1, VERIFIED)
        verify(utxoLedgerPersistenceService).updateStatus(TX_ID_2, VERIFIED)
        verify(utxoLedgerPersistenceService).updateStatus(TX_ID_3, VERIFIED)
    }

    @Test
    fun `updates the statuses of transactions that pass verification even when a later transaction fails verification`() {
        whenever(ledgerTransaction3.inputStateAndRefs).thenReturn(listOf(getExampleInvalidStateAndRefImpl()))
        whenever(utxoLedgerTransactionVerificationService.verify(ledgerTransaction3)).thenThrow(VERIFICATION_EXCEPTION)
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(utxoLedgerPersistenceService).updateStatus(TX_ID_1, VERIFIED)
        verify(utxoLedgerPersistenceService).updateStatus(TX_ID_2, VERIFIED)
        verify(utxoLedgerPersistenceService, never()).updateStatus(TX_ID_3, VERIFIED)
    }

    @Test
    fun `returns false when a single transaction fails verification`() {
        whenever(ledgerTransaction1.inputStateAndRefs).thenReturn(listOf(getExampleInvalidStateAndRefImpl()))
        whenever(utxoLedgerTransactionVerificationService.verify(ledgerTransaction1)).thenThrow(VERIFICATION_EXCEPTION)
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(ledgerTransaction2, never()).inputStateAndRefs
        verify(ledgerTransaction3, never()).inputStateAndRefs
        verify(utxoLedgerPersistenceService, never()).updateStatus(any(), eq(VERIFIED))
    }

    @Test
    fun `returns false when a single transaction has invalid signatures`() {
        whenever(transaction1.verifySignatures()).thenThrow(
            TransactionMissingSignaturesException(
                TX_ID_1,
                setOf(signatory),
                "Invalid signature"
            )
        )
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(ledgerTransaction2, never()).inputStateAndRefs
        verify(ledgerTransaction3, never()).inputStateAndRefs
        verify(utxoLedgerPersistenceService, never()).updateStatus(any(), eq(VERIFIED))
    }

    @Test
    fun `returns false when a single transaction does not have notary signatures`() {
        whenever(transaction1.verifyNotarySignatureAttached()).thenThrow(
            TransactionMissingSignaturesException(
                TX_ID_1,
                setOf(signatory),
                "Missing notary signature"
            )
        )
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(ledgerTransaction2, never()).inputStateAndRefs
        verify(ledgerTransaction3, never()).inputStateAndRefs
        verify(utxoLedgerPersistenceService, never()).updateStatus(any(), eq(VERIFIED))
    }

    @Test
    fun `throws an exception if a transaction cannot be retrieved from the database`() {
        whenever(utxoLedgerPersistenceService.find(TX_ID_1, UNVERIFIED)).thenReturn(null)
        assertThatThrownBy { transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort()) }.isExactlyInstanceOf(
            CordaRuntimeException::class.java
        )
        verify(utxoLedgerPersistenceService, never()).updateStatus(any(), eq(VERIFIED))
    }

    @Test
    fun `updates the statuses of transactions that pass verification even when a later transaction cannot be retrieved from the database`() {
        whenever(utxoLedgerPersistenceService.find(TX_ID_3, UNVERIFIED)).thenReturn(null)
        assertThatThrownBy { transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort()) }.isExactlyInstanceOf(
            CordaRuntimeException::class.java
        )
        verify(utxoLedgerPersistenceService).updateStatus(TX_ID_1, VERIFIED)
        verify(utxoLedgerPersistenceService).updateStatus(TX_ID_2, VERIFIED)
        verify(utxoLedgerPersistenceService, never()).updateStatus(TX_ID_3, VERIFIED)
    }

    private fun topologicalSort() = TopologicalSort().apply {
        add(TX_ID_3, emptySet())
        add(TX_ID_2, emptySet())
        add(TX_ID_1, emptySet())
    }
}