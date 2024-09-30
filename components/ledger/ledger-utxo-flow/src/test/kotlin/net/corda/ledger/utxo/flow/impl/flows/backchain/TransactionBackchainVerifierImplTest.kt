package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationException
import net.corda.ledger.utxo.testkit.getExampleInvalidStateAndRefImpl
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.VisibilityChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val visibilityChecker = mock<VisibilityChecker>()

    private val transaction1 = mock<UtxoSignedLedgerTransaction>()
    private val transaction2 = mock<UtxoSignedLedgerTransaction>()
    private val transaction3 = mock<UtxoSignedLedgerTransaction>()

    private val signatory = mock<PublicKey>()
    private val transactionBackchainVerifier = TransactionBackchainVerifierImpl(
        utxoLedgerPersistenceService,
        utxoLedgerTransactionVerificationService,
        visibilityChecker
    )

    @BeforeEach
    fun beforeEach() {
        whenever(
            utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(
                TX_ID_1,
                UNVERIFIED
            )
        ).thenReturn(transaction1 to UNVERIFIED)
        whenever(
            utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(
                TX_ID_2,
                UNVERIFIED
            )
        ).thenReturn(transaction2 to UNVERIFIED)
        whenever(
            utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(
                TX_ID_3,
                UNVERIFIED
            )
        ).thenReturn(transaction3 to UNVERIFIED)
        whenever(transaction1.id).thenReturn(TX_ID_1)
        whenever(transaction1.outputStateAndRefs).thenReturn(emptyList())
        whenever(transaction2.id).thenReturn(TX_ID_2)
        whenever(transaction2.outputStateAndRefs).thenReturn(listOf(getExampleStateAndRefImpl()))
        whenever(transaction3.id).thenReturn(TX_ID_3)
        whenever(transaction3.outputStateAndRefs).thenReturn(listOf(getExampleStateAndRefImpl()))
        whenever(visibilityChecker.containsMySigningKeys(any())).thenReturn(true)
    }

    @Test
    fun `returns true when all transactions pass verification`() {
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isTrue
    }

    @Test
    fun `persists transaction as VERIFIED when all transactions pass verification`() {
        transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())
        verify(utxoLedgerPersistenceService).persist(transaction1, VERIFIED, emptyList())
        verify(utxoLedgerPersistenceService).persist(transaction2, VERIFIED, listOf(0))
        verify(utxoLedgerPersistenceService).persist(transaction3, VERIFIED, listOf(0))
    }

    @Test
    fun `updates the statuses of transactions that pass verification even when a later transaction fails verification`() {
        whenever(transaction3.inputStateAndRefs).thenReturn(listOf(getExampleInvalidStateAndRefImpl()))
        whenever(utxoLedgerTransactionVerificationService.verify(transaction3)).thenThrow(VERIFICATION_EXCEPTION)
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(utxoLedgerPersistenceService).persist(transaction1, VERIFIED, emptyList())
        verify(utxoLedgerPersistenceService).persist(transaction2, VERIFIED, listOf(0))
        verify(utxoLedgerPersistenceService, never()).persist(eq(transaction3), eq(VERIFIED), any())
    }

    @Test
    fun `returns false when a single transaction fails verification`() {
        whenever(transaction1.inputStateAndRefs).thenReturn(listOf(getExampleInvalidStateAndRefImpl()))
        whenever(utxoLedgerTransactionVerificationService.verify(transaction1)).thenThrow(VERIFICATION_EXCEPTION)
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(transaction2, never()).inputStateAndRefs
        verify(transaction3, never()).inputStateAndRefs
        verify(utxoLedgerPersistenceService, never()).persist(any(), eq(VERIFIED), any())
    }

    @Test
    fun `returns false when a single transaction has invalid signatures`() {
        whenever(transaction1.verifySignatorySignatures()).thenThrow(
            TransactionMissingSignaturesException(
                TX_ID_1,
                setOf(signatory),
                "Invalid signature"
            )
        )
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(transaction2, never()).inputStateAndRefs
        verify(transaction3, never()).inputStateAndRefs
        verify(utxoLedgerPersistenceService, never()).persist(any(), eq(VERIFIED), any())
    }

    @Test
    fun `returns false when a single transaction does not have notary signatures`() {
        whenever(transaction1.verifyAttachedNotarySignature()).thenThrow(
            TransactionMissingSignaturesException(
                TX_ID_1,
                setOf(signatory),
                "Missing notary signature"
            )
        )
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(transaction2, never()).inputStateAndRefs
        verify(transaction3, never()).inputStateAndRefs
        verify(utxoLedgerPersistenceService, never()).persist(any(), eq(VERIFIED), any())
    }

    @Test
    fun `throws an exception if a transaction cannot be retrieved from the database`() {
        whenever(utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(TX_ID_1, UNVERIFIED)).thenReturn(null)
        assertThrows<CordaRuntimeException> { transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort()) }
        verify(utxoLedgerPersistenceService, never()).persist(any(), eq(VERIFIED), any())
    }

    @Test
    fun `returns false if a transaction comes as invalid from the database`() {
        whenever(
            utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(TX_ID_1, UNVERIFIED)
        ).thenReturn(null to TransactionStatus.INVALID)
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isFalse
        verify(utxoLedgerPersistenceService, never()).persist(any(), eq(VERIFIED), any())
    }

    @Test
    fun `updates the statuses of transactions that pass verification even when a later transaction cannot be retrieved from the database`() {
        whenever(utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(TX_ID_3, UNVERIFIED)).thenReturn(null)
        assertThrows<CordaRuntimeException> { transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort()) }

        verify(utxoLedgerPersistenceService).persist(transaction1, VERIFIED, emptyList())
        verify(utxoLedgerPersistenceService).persist(transaction2, VERIFIED, listOf(0))
        verify(utxoLedgerPersistenceService, never()).persist(eq(transaction3), eq(VERIFIED), any())
    }

    @Test
    fun `returns true if a transaction comes as already verified from the database`() {
        whenever(utxoLedgerPersistenceService.findSignedLedgerTransactionWithStatus(TX_ID_1, UNVERIFIED)).thenReturn(null to VERIFIED)
        assertThat(transactionBackchainVerifier.verify(setOf(RESOLVING_TX_ID), topologicalSort())).isTrue
    }

    private fun topologicalSort() = TopologicalSort().apply {
        add(TX_ID_3, emptySet())
        add(TX_ID_2, emptySet())
        add(TX_ID_1, emptySet())
    }
}
