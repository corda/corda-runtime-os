package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.flow.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class TransactionBackchainReceiverFlowTest {

    private companion object {
        val TX_ID_1 = SecureHash("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHash("SHA", byteArrayOf(3, 3, 3, 3))
        val TX_ID_3 = SecureHash("SHA", byteArrayOf(4, 4, 4, 4))
        val TX_3_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_3, 0)
        val TX_3_INPUT_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_3, 1)

        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_3, 0)
        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_3, 1)

        val PACKAGE_SUMMARY = CordaPackageSummaryImpl("name", "version", "hash", "checksum")
    }

    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val session = mock<FlowSession>()

    private val retrievedTransaction1 = mock<UtxoSignedTransaction>()
    private val retrievedTransaction2 = mock<UtxoSignedTransaction>()
    private val retrievedTransaction3 = mock<UtxoSignedTransaction>()

    private val ledgerTransaction1 = mock<UtxoLedgerTransaction>()
    private val ledgerTransaction2 = mock<UtxoLedgerTransaction>()
    private val ledgerTransaction3 = mock<UtxoLedgerTransaction>()

    @BeforeEach
    fun beforeEach() {
        whenever(retrievedTransaction1.toLedgerTransaction()).thenReturn(ledgerTransaction1)
        whenever(retrievedTransaction2.toLedgerTransaction()).thenReturn(ledgerTransaction2)
        whenever(retrievedTransaction3.toLedgerTransaction()).thenReturn(ledgerTransaction3)
    }

    @Test
    fun `a resolved transaction has its dependencies retrieved from its peer and persisted`() {
        whenever(utxoLedgerPersistenceService.find(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2),
            listOf(retrievedTransaction3)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(ledgerTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(ledgerTransaction1.referenceInputStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(ledgerTransaction2.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_2))
        whenever(ledgerTransaction2.referenceInputStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2))

        whenever(retrievedTransaction3.id).thenReturn(TX_ID_3)
        whenever(ledgerTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction3.referenceInputStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_3, TX_ID_2, TX_ID_1))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_2)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_3)))
        verify(session).send(TransactionBackchainRequest.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `a transaction without any dependencies does not need resolving`() {
        assertThat(callTransactionBackchainReceiverFlow(emptySet()).complete()).isEmpty()

        verifyNoInteractions(session)
        verifyNoInteractions(utxoLedgerPersistenceService)
    }

    @Test
    fun `receiving a transaction that is stored locally as UNVERIFIED has its dependencies added to the transactions to retrieve`() {
        whenever(utxoLedgerPersistenceService.find(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2),
            listOf(retrievedTransaction3)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(ledgerTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(ledgerTransaction1.referenceInputStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(ledgerTransaction2.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_2))
        whenever(ledgerTransaction2.referenceInputStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2))

        whenever(retrievedTransaction3.id).thenReturn(TX_ID_3)
        whenever(ledgerTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction3.referenceInputStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_3, TX_ID_2, TX_ID_1))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_2)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_3)))
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `receiving a transaction that is stored locally as VERIFIED does not have its dependencies added to the transactions to retrieve`() {
        whenever(utxoLedgerPersistenceService.find(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.VERIFIED to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(ledgerTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(ledgerTransaction1.referenceInputStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(ledgerTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(ledgerTransaction2.referenceInputStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_2))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_2)))
        verify(session, never()).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_3)))
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `receiving a transaction that was not included in the requested batch of transactions throws an exception`() {
        whenever(utxoLedgerPersistenceService.find(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(ledgerTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(ledgerTransaction1.referenceInputStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)

        assertThatThrownBy { callTransactionBackchainReceiverFlow(setOf(TX_ID_1)) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequest.Get(setOf(TX_ID_1)))
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
    }

    private fun callTransactionBackchainReceiverFlow(originalTransactionsToRetrieve: Set<SecureHash>): TopologicalSort {
        return TransactionBackchainReceiverFlow(
            SecureHash("SHA", byteArrayOf(1, 1, 1, 1)),
            originalTransactionsToRetrieve, session
        ).apply { utxoLedgerPersistenceService = this@TransactionBackchainReceiverFlowTest.utxoLedgerPersistenceService }
            .call()
    }
}