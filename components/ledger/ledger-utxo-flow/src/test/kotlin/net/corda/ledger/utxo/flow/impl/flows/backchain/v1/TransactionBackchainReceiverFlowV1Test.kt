package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.crypto.core.SecureHashImpl
import net.corda.flow.application.services.FlowConfigService
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.utxo.flow.impl.UtxoLedgerMetricRecorder
import net.corda.ledger.utxo.flow.impl.flows.backchain.TopologicalSort
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackChainResolutionVersion
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class TransactionBackchainReceiverFlowV1Test {

    private companion object {
        val TX_ID_1 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
        val TX_ID_2 = SecureHashImpl("SHA", byteArrayOf(3, 3, 3, 3))

        // Root transaction
        val TX_ID_3 = SecureHashImpl("SHA", byteArrayOf(4, 4, 4, 4))
        val TX_3_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_3, 0)
        val TX_3_INPUT_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_3, 1)

        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1 = StateRef(TX_ID_3, 0)
        val TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2 = StateRef(TX_ID_3, 1)

        val PACKAGE_SUMMARY = CordaPackageSummaryImpl("name", "version", "hash", "checksum")

        const val BACKCHAIN_BATCH_CONFIG_PATH = "backchain.batchSize"
        const val BACKCHAIN_BATCH_DEFAULT_SIZE = 1
    }

    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val utxoLedgerMetricRecorder = mock<UtxoLedgerMetricRecorder>()
    private val utxoLedgerGroupParametersPersistenceService = mock<UtxoLedgerGroupParametersPersistenceService>()
    private val signedGroupParametersVerifier = mock<SignedGroupParametersVerifier>()
    private val flowConfigService = mock<FlowConfigService>()

    private val session = mock<FlowSession>()

    private val retrievedTransaction1 = mock<UtxoSignedTransaction>()
    private val retrievedTransaction2 = mock<UtxoSignedTransaction>()
    private val retrievedTransaction3 = mock<UtxoSignedTransaction>()

    @BeforeEach
    fun setup() {
        val utxoConfig = mock<SmartConfig> {
            on { getInt(BACKCHAIN_BATCH_CONFIG_PATH) } doReturn BACKCHAIN_BATCH_DEFAULT_SIZE
        }
        whenever(flowConfigService.getConfig(ConfigKeys.UTXO_LEDGER_CONFIG)).thenReturn(utxoConfig)
    }

    @Test
    fun `a resolved transaction has its dependencies retrieved from its peer and persisted`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2),
            listOf(retrievedTransaction3)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_2))
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2))

        whenever(retrievedTransaction3.id).thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_3, TX_ID_2, TX_ID_1))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)

        verifyNoInteractions(
            utxoLedgerGroupParametersPersistenceService,
            signedGroupParametersVerifier
        )
    }

    @Test
    fun `a transaction without any dependencies does not need resolving`() {
        assertThat(callTransactionBackchainReceiverFlow(emptySet()).complete()).isEmpty()

        verify(session).send(TransactionBackchainRequestV1.Stop)
        verifyNoMoreInteractions(session)
        verifyNoInteractions(
            utxoLedgerGroupParametersPersistenceService,
            signedGroupParametersVerifier
        )
    }

    @Test
    fun `receiving a transaction that is stored locally as UNVERIFIED has its dependencies added to the transactions to retrieve`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2),
            listOf(retrievedTransaction3)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_2))
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2))

        whenever(retrievedTransaction3.id).thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_3, TX_ID_2, TX_ID_1))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
        verifyNoInteractions(
            utxoLedgerGroupParametersPersistenceService,
            signedGroupParametersVerifier
        )
    }

    @Test
    fun `receiving a transaction that is stored locally as VERIFIED does not have its dependencies added to the transactions to retrieve`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.VERIFIED to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_2))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session, never()).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
        verifyNoInteractions(
            utxoLedgerGroupParametersPersistenceService,
            signedGroupParametersVerifier
        )
    }

    @Test
    fun `receiving only transactions that are stored locally as VERIFIED does not have their dependencies added to the transactions to retrieve and stops resolution`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.VERIFIED to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(emptyList<SecureHash>())

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session, never()).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
        verifyNoInteractions(
            utxoLedgerGroupParametersPersistenceService,
            signedGroupParametersVerifier
        )
    }

    @Test
    fun `receiving a transaction that was not included in the requested batch of transactions throws an exception`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)

        assertThatThrownBy { callTransactionBackchainReceiverFlow(setOf(TX_ID_1)) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verifyNoInteractions(
            utxoLedgerGroupParametersPersistenceService,
            signedGroupParametersVerifier
        )
    }

    @Test
    fun `receiving a transaction twice at different points in the backchain retrieves the transaction once and correctly places it in the sorted transactions`() {
        /*
       The transaction chain:
                    tx4
                   /   \
               tx2      \
               /         \
           tx1            tx5
            \            /
             \         /
              \      /
               tx3 /

       TX5 will cause TX3 and TX4 to be fetched.
       TX3 will cause TX1 to be fetched.
       TX4 will cause TX2 to be fetched
       TX2 will not cause TX1 to be fetched, but it will cause TX1 to be placed before TX2 and TX3 in the sorted transactions because both
       transactions depend on it.

       TX5 is not referenced in the test because the dependencies of the transaction are passed into the flow as IDs.
       */

        val transactionId4 = SecureHashImpl("SHA", byteArrayOf(4, 4, 4, 4))
        val transactionId3 = SecureHashImpl("SHA", byteArrayOf(3, 3, 3, 3))
        val transactionId2 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
        val transactionId1 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))

        val transaction4 = mock<UtxoSignedTransaction>()
        val transaction3 = mock<UtxoSignedTransaction>()
        val transaction2 = mock<UtxoSignedTransaction>()
        val transaction1 = mock<UtxoSignedTransaction>()

        val transaction2StateRef = StateRef(transactionId2, 0)
        val transaction1StateRef0 = StateRef(transactionId1, 0)
        val transaction1StateRef1 = StateRef(transactionId1, 1)

        whenever(transaction4.id).thenReturn(transactionId4)
        whenever(transaction4.inputStateRefs).thenReturn(listOf(transaction2StateRef))

        whenever(transaction3.id).thenReturn(transactionId3)
        whenever(transaction3.inputStateRefs).thenReturn(listOf(transaction1StateRef1))

        whenever(transaction2.id).thenReturn(transactionId2)
        whenever(transaction2.inputStateRefs).thenReturn(listOf(transaction1StateRef0))

        whenever(transaction1.id).thenReturn(transactionId1)
        whenever(transaction1.inputStateRefs).thenReturn(emptyList())

        whenever(utxoLedgerPersistenceService.findSignedTransaction(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(transaction3),
            listOf(transaction4),
            listOf(transaction1),
            listOf(transaction2)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        assertThat(callTransactionBackchainReceiverFlow(setOf(transactionId3, transactionId4)).complete()).isEqualTo(
            listOf(
                transactionId1,
                transactionId2,
                transactionId4,
                transactionId3
            )
        )

        session.inOrder {
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId3)))
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId4)))
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId1)))
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId2)))
            Unit

        }

        utxoLedgerPersistenceService.inOrder {
            verify().persistIfDoesNotExist(transaction3, UNVERIFIED)
            verify().persistIfDoesNotExist(transaction4, UNVERIFIED)
            verify().persistIfDoesNotExist(transaction1, UNVERIFIED)
            verify().persistIfDoesNotExist(transaction2, UNVERIFIED)
            Unit
        }
        verifyNoInteractions(
            utxoLedgerGroupParametersPersistenceService,
            signedGroupParametersVerifier
        )
    }

    private fun callTransactionBackchainReceiverFlow(originalTransactionsToRetrieve: Set<SecureHash>): TopologicalSort {
        return TransactionBackchainReceiverFlowV1(
            setOf(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))),
            originalTransactionsToRetrieve, session,
            TransactionBackChainResolutionVersion.V1
        ).apply {
            utxoLedgerPersistenceService = this@TransactionBackchainReceiverFlowV1Test.utxoLedgerPersistenceService
            utxoLedgerMetricRecorder = this@TransactionBackchainReceiverFlowV1Test.utxoLedgerMetricRecorder
            utxoLedgerGroupParametersPersistenceService = this@TransactionBackchainReceiverFlowV1Test.utxoLedgerGroupParametersPersistenceService
            signedGroupParametersVerifier = this@TransactionBackchainReceiverFlowV1Test.signedGroupParametersVerifier
            flowConfigService = this@TransactionBackchainReceiverFlowV1Test.flowConfigService
        }.call()
    }
}