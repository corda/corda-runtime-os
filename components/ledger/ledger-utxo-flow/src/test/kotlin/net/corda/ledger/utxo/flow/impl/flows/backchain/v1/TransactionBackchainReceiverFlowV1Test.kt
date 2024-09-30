package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.crypto.core.SecureHashImpl
import net.corda.flow.application.services.FlowConfigService
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus.INVALID
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.lib.utxo.flow.impl.groupparameters.SignedGroupParametersVerifier
import net.corda.ledger.lib.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.ledger.utxo.flow.impl.UtxoLedgerMetricRecorder
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TopologicalSort
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.SignedGroupParameters
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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

        const val BACKCHAIN_BATCH_CONFIG_PATH = "backchain.batchSize"
        const val BACKCHAIN_BATCH_DEFAULT_SIZE = 1
    }

    private val groupParameters = mock<SignedGroupParameters>()
    private val groupParameters2 = mock<SignedGroupParameters>()

    private val groupParametersHash1 = SecureHashImpl("SHA", byteArrayOf(101, 101, 101, 101))
    private val groupParametersHash2 = SecureHashImpl("SHA", byteArrayOf(102, 102, 102, 102))

    private val tx1Metadata = mock<TransactionMetadataInternal>()
    private val tx2Metadata = mock<TransactionMetadataInternal>()
    private val tx3Metadata = mock<TransactionMetadataInternal>()

    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val utxoLedgerMetricRecorder = mock<UtxoLedgerMetricRecorder>()
    private val utxoLedgerGroupParametersPersistenceService = mock<UtxoLedgerGroupParametersPersistenceService>()
    private val signedGroupParametersVerifier = mock<SignedGroupParametersVerifier>()
    private val flowConfigService = mock<FlowConfigService>()

    private val session = mock<FlowSession>()

    private val retrievedTransaction1 = mock<UtxoSignedLedgerTransaction>()
    private val retrievedTransaction2 = mock<UtxoSignedLedgerTransaction>()
    private val retrievedTransaction3 = mock<UtxoSignedLedgerTransaction>()

    @BeforeEach
    fun setup() {
        val utxoConfig = mock<SmartConfig> {
            on { getInt(BACKCHAIN_BATCH_CONFIG_PATH) } doReturn BACKCHAIN_BATCH_DEFAULT_SIZE
        }
        whenever(flowConfigService.getConfig(ConfigKeys.UTXO_LEDGER_CONFIG)).thenReturn(utxoConfig)
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(emptyMap())
    }

    /**
     * This test is simulating a scenario where we want to fetch 2 transactions but we already have one of them
     * in our database. In that case it will be removed from the `transactionsToRetrieve` list and will not be
     * requested.
     */
    @Test
    fun `transaction will not be requested if it is present in the database (VERIFIED) and DB data will not be in the topological sort`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(mapOf(TX_ID_1 to VERIFIED))

        whenever(session.sendAndReceive(eq(List::class.java), eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_2))))).thenReturn(
            listOf(retrievedTransaction2)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)

        // No need for dependencies to test this scenario
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete())
            // TX_ID_1 is already present in the DB so should not be retrieved and the transaction from the database
            // should not be part of the topological sort output
            .isEqualTo(listOf(TX_ID_2))
    }

    /**
     * This test is simulating a scenario where:
     *
     * TX1 (in DB)-------> TX2 (not in DB)
     *  |           |
     *  |           |
     *  |          \/
     *  |-------> TX3 (in DB)
     *
     *  Both TX1 and TX2 reference TX3.
     *  TX1 and TX3 are in the database but TX2 is not.
     *  This way TX1's dependencies will be expanded first, TX2 will be added to the "to retrieve" set and will be retrieved.
     *  However, TX3 will already be part of the topological sort by the time we get to TX2, so its dependencies should
     *  not be retrieved.
     */
    @Test
    fun `transaction that is in the DB and referenced by multiple transactions - one in DB and one retrievable will only be retrieved once`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                    TX_ID_3 to UNVERIFIED
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        // TX1
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(
            listOf(
                StateRef(TX_ID_2, 0),
                StateRef(TX_ID_3, 0)
            )
        )
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)

        // TX3
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_3), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction3, UNVERIFIED))

        whenever(retrievedTransaction3.id).thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.metadata).thenReturn(tx1Metadata)

        // TX2
        whenever(session.sendAndReceive(eq(List::class.java), eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_2))))).thenReturn(
            listOf(retrievedTransaction2)
        )

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(listOf(StateRef(TX_ID_3, 0)))
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(mock())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete())
            .containsExactlyInAnyOrder(TX_ID_1, TX_ID_2, TX_ID_3)

        verify(session, times(1)).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        )

        verify(session, never()).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_1, TX_ID_3)))
        )

        verify(session, never()).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        )

        verify(session, never()).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        )

        // Since TX3 should already be part of the topological sort by the time we fetch TX2,
        // it shouldn't be fetched from the DB again
        verify(utxoLedgerPersistenceService, times(1)).findSignedTransactionWithStatus(TX_ID_1, UNVERIFIED)
        verify(utxoLedgerPersistenceService, times(1)).findSignedTransactionWithStatus(TX_ID_3, UNVERIFIED)
    }

    /**
     * This test is simulating a scenario where we want to fetch one transaction, but we already have that in our database.
     * However, it is in an unverified status, and we don't know its group parameters. In that case it will be kept in the
     * `transactionsToRetrieve` list and WILL BE requested alongside with its dependencies.
     */
    @Test
    fun `transaction will be requested if it is present in the database (UNVERIFIED) and its group params are not known`() {
        // Have a transaction with TX_ID_2 that is unverified, but it's in the database and has dependencies
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(mapOf(TX_ID_2 to UNVERIFIED))

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_2), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(
            session.sendAndReceive(
                eq(List::class.java),
                eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
            )
        ).thenReturn(
            listOf(retrievedTransaction1)
        )
        whenever(
            session.sendAndReceive(
                eq(List::class.java),
                eq(TransactionBackchainRequestV1.Get(setOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId)))
            )
        ).thenReturn(
            listOf(retrievedTransaction2)
        )

        whenever(retrievedTransaction2.id).thenReturn(TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        // We have no information about the transaction's group parameters
        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(null)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        // Both the original transaction and its dependency should be retrieved
        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_2)).complete())
            .isEqualTo(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId, TX_ID_2))
    }

    /**
     * This test is simulating a scenario where we want to fetch a transaction that has one dependency and
     * that dependency has another dependency. Both the transaction and its dependencies are in the database
     * with UNVERIFIED status. However, we don't know the last dependency's group parameters. In this case,
     * we should go to the counterparty to retrieve.
     */
    @Test
    fun `dependency of dependency of transaction will be fetched if it is in the database but group params not known`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                    TX_ID_2 to UNVERIFIED,
                    TX_ID_3 to UNVERIFIED,
                )
            )

        whenever(tx1Metadata.getMembershipGroupParametersHash())
            .thenReturn(groupParametersHash1.toString())

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any()))
            .thenReturn(groupParameters)

        whenever(
            session.sendAndReceive(
                eq(SignedGroupParameters::class.java),
                eq(TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
            )
        ).thenReturn(groupParameters)

        whenever(
            session.sendAndReceive(
                eq(SignedGroupParameters::class.java),
                eq(TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash2))
            )
        ).thenReturn(groupParameters2)

        whenever(groupParameters.hash)
            .thenReturn(groupParametersHash1)

        whenever(groupParameters2.hash)
            .thenReturn(groupParametersHash2)

        // Base transaction
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))

        whenever(retrievedTransaction1.id)
            .thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs)
            .thenReturn(listOf(StateRef(TX_ID_2, 0)))
        whenever(retrievedTransaction1.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata)
            .thenReturn(tx1Metadata)

        // Dependency
        whenever(
            utxoLedgerPersistenceService.findSignedTransactionWithStatus(
                eq(TX_ID_2),
                eq(UNVERIFIED)
            )
        ).thenReturn(Pair(retrievedTransaction2, UNVERIFIED))

        whenever(retrievedTransaction2.id)
            .thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs)
            .thenReturn(listOf(StateRef(TX_ID_3, 0)))
        whenever(retrievedTransaction2.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction2.metadata)
            .thenReturn(tx1Metadata)

        // Dependency of dependency
        whenever(
            utxoLedgerPersistenceService.findSignedTransactionWithStatus(
                eq(TX_ID_3),
                eq(UNVERIFIED)
            )
        ).thenReturn(Pair(retrievedTransaction3, UNVERIFIED))

        whenever(retrievedTransaction3.id)
            .thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction3.metadata)
            .thenReturn(tx2Metadata)

        whenever(tx2Metadata.getMembershipGroupParametersHash())
            .thenReturn(groupParametersHash2.toString())

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(mock())

        // We have no information about TX3's group parameters
        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash2))
            .thenReturn(null)

        whenever(
            session.sendAndReceive(
                eq(List::class.java),
                eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
            )
        ).thenReturn(
            listOf(retrievedTransaction3)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        // Since both the base, dependency and dependency of dependency transaction were present in the database,
        // but TX_ID_4's group params not know it should have been retrieved and all should be in the topological sort
        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete())
            .containsExactlyInAnyOrder(TX_ID_1, TX_ID_2, TX_ID_3)

        verify(session, times(1)).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        )
    }

    /**
     * This test is simulating a scenario where we want to fetch one transaction, but we already have that in our database.
     * However, it is in an unverified status. In that case it will be removed from the `transactionsToRetrieve` list
     * and will not be requested but its dependencies WILL BE requested.
     */
    @Test
    fun `transaction will not be requested if it is present in the database (UNVERIFIED) but their dependencies will be`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(mapOf(TX_ID_2 to UNVERIFIED))

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_2), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)

        whenever(
            session.sendAndReceive(
                eq(List::class.java),
                eq(TransactionBackchainRequestV1.Get(setOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId)))
            )
        ).thenReturn(
            listOf(retrievedTransaction2)
        )
        whenever(retrievedTransaction2.id).thenReturn(TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(mock())

        // Since TX_ID_2 is already in the DB it will not be retrieved but TX_3_INPUT_DEPENDENCY_STATE_REF_1 will be
        // both will be in the topological sort though
        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_2)).complete())
            .containsExactlyInAnyOrder(TX_ID_2, TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId)

        verify(session, times(1)).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId)))
        )
    }

    /**
     * This test is simulating a scenario where we want to fetch a transaction that has one dependency.
     * Both the transaction and its dependency is in the database but the dependency has an INVALID status.
     * The flow should throw an exception in this case.
     */
    @Test
    fun `flow will throw exception if any of the transactions are invalid`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_2 to UNVERIFIED,
                    TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId to INVALID
                )
            )

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_2), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(emptyList())

        whenever(retrievedTransaction2.id).thenReturn(TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())

        val exception = assertThrows<InvalidBackchainException> {
            callTransactionBackchainReceiverFlow(setOf(TX_ID_2))
        }

        assertThat(exception).hasStackTraceContaining(
            "Found the following invalid transaction(s) during back-chain resolution: " +
                "[${TX_3_INPUT_DEPENDENCY_STATE_REF_1.transactionId}]. Back-chain resolution cannot be continued."
        )
    }

    /**
     * This test is simulating a scenario where we want to fetch a transaction that has one dependency and
     * that dependency has another dependency. Both the transaction and its dependencies are in the database
     * with UNVERIFIED status. In this case, we shouldn't go to the counterparty since we have everything in
     * our database.
     */
    @Test
    fun `dependency of dependency of transaction will not be fetched if it is in the database`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                    TX_ID_2 to UNVERIFIED,
                    TX_ID_3 to UNVERIFIED,
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        // Base transaction
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))

        whenever(retrievedTransaction1.id)
            .thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs)
            .thenReturn(listOf(StateRef(TX_ID_2, 0)))
        whenever(retrievedTransaction1.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)

        // Dependency
        whenever(
            utxoLedgerPersistenceService.findSignedTransactionWithStatus(
                eq(TX_ID_2),
                eq(UNVERIFIED)
            )
        ).thenReturn(Pair(retrievedTransaction2, UNVERIFIED))

        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)

        whenever(retrievedTransaction2.id)
            .thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs)
            .thenReturn(listOf(StateRef(TX_ID_3, 0)))
        whenever(retrievedTransaction2.referenceStateRefs)
            .thenReturn(emptyList())

        // Dependency of dependency
        whenever(
            utxoLedgerPersistenceService.findSignedTransactionWithStatus(
                eq(TX_ID_3),
                eq(UNVERIFIED)
            )
        ).thenReturn(Pair(retrievedTransaction3, UNVERIFIED))

        whenever(retrievedTransaction3.id)
            .thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction3.metadata).thenReturn(tx1Metadata)

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(mock())

        // Since both the base, dependency and dependency of dependency transaction were present in the database,
        // nothing should have been retrieved but all three should be in the topological sort
        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete())
            .containsExactlyInAnyOrder(TX_ID_1, TX_ID_2, TX_ID_3)

        verify(session, never()).sendAndReceive(
            eq(List::class.java),
            any()
        )
    }

    /**
     * This test is simulating a scenario where we want to fetch a transaction that has one dependency and
     * that dependency has another dependency. The main transaction is in the database and so its dependency.
     * However, the dependency of the dependency is not in the database, so we need to retrieve that from the
     * counterparty.
     */
    @Test
    fun `dependency of dependency of transaction will be fetched if it is not in the database`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                    TX_ID_2 to UNVERIFIED
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        // Base transaction
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)

        whenever(retrievedTransaction1.id)
            .thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs)
            .thenReturn(listOf(StateRef(TX_ID_2, 0)))
        whenever(retrievedTransaction1.referenceStateRefs)
            .thenReturn(emptyList())

        // Dependency
        whenever(
            utxoLedgerPersistenceService.findSignedTransactionWithStatus(
                eq(TX_ID_2),
                eq(UNVERIFIED)
            )
        ).thenReturn(Pair(retrievedTransaction2, UNVERIFIED))
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)

        whenever(retrievedTransaction2.id)
            .thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs)
            .thenReturn(listOf(StateRef(TX_ID_3, 0)))
        whenever(retrievedTransaction2.referenceStateRefs)
            .thenReturn(emptyList())

        // Dependency of dependency
        whenever(
            utxoLedgerPersistenceService.findSignedTransactionWithStatus(
                eq(TX_ID_3),
                eq(UNVERIFIED)
            )
        ).thenReturn(Pair(retrievedTransaction3, UNVERIFIED))
        whenever(retrievedTransaction3.metadata).thenReturn(tx1Metadata)

        whenever(retrievedTransaction3.id)
            .thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs)
            .thenReturn(emptyList())

        whenever(
            session.sendAndReceive(
                eq(List::class.java),
                eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
            )
        ).thenReturn(
            listOf(retrievedTransaction3)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(mock())

        // Since only the base and dependency transaction were present in the database,
        // TX_ID_3 should have been retrieved but all three should be in the topological sort
        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete())
            .containsExactlyInAnyOrder(TX_ID_1, TX_ID_2, TX_ID_3)

        verify(session, times(1)).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        )
    }

    /**
     * This test is simulating a scenario where the transaction's status was originally UNVERIFIED in the database,
     * but then it changed to INVALID while the flow was in flight.
     */
    @Test
    fun `if transaction status changed to invalid from unverified exception will be thrown`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                )
            )
            .thenReturn(
                mapOf(
                    TX_ID_1 to INVALID
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, INVALID))

        assertThrows<InvalidBackchainException> {
            callTransactionBackchainReceiverFlow(setOf(TX_ID_1))
        }

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED))

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionIdsAndStatuses(eq(listOf(TX_ID_1)))
    }

    /**
     * This test is simulating a scenario where the transaction's status was originally UNVERIFIED in the database,
     * but then it disappeared from the DB.
     */
    @Test
    fun `if transaction status was unverified then it disappeared from the DB it will be retrieved`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(null)

        whenever(
            session.sendAndReceive(
                eq(List::class.java),
                eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
            )
        ).thenReturn(
            listOf(retrievedTransaction1)
        )

        whenever(retrievedTransaction1.id)
            .thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction1.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata)
            .thenReturn(tx1Metadata)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(mock())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete()).containsExactlyInAnyOrder(TX_ID_1)

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED))

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionIdsAndStatuses(eq(listOf(TX_ID_1)))

        verify(session, times(1)).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        )
    }

    /**
     * This test is simulating a scenario where the transaction's status was originally UNVERIFIED in the database,
     * but then its status could not be fetched.
     */
    @Test
    fun `if transaction status was unverified then its status could not be fetched it will be retrieved`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(null, UNVERIFIED))

        whenever(retrievedTransaction1.id)
            .thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction1.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata)
            .thenReturn(tx1Metadata)

        whenever(
            session.sendAndReceive(
                eq(List::class.java),
                eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
            )
        ).thenReturn(
            listOf(retrievedTransaction1)
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(mock())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete()).containsExactlyInAnyOrder(TX_ID_1)

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED))

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionIdsAndStatuses(eq(listOf(TX_ID_1)))

        verify(session, times(1)).sendAndReceive(
            eq(List::class.java),
            eq(TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        )
    }

    /**
     * This test is simulating a scenario where the transaction's status was originally UNVERIFIED in the database,
     * but then it changed to VERIFIED while the flow was in flight.
     */
    @Test
    fun `if transaction status changed to verified from unverified it will not be retrieved`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, VERIFIED))

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete()).isEmpty()

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED))

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionIdsAndStatuses(eq(listOf(TX_ID_1)))

        verify(session, never())
            .sendAndReceive(eq(List::class.java), any())
    }

    /**
     * This test is simulating a scenario where a dependency's status was originally UNVERIFIED in the database,
     * but then it changed to VERIFIED while the flow was in flight.
     */
    @Test
    fun `if transaction's dependency status changed to verified from unverified it will not be retrieved`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionIdsAndStatuses(any()))
            .thenReturn(
                mapOf(
                    TX_ID_1 to UNVERIFIED,
                    TX_ID_2 to UNVERIFIED
                )
            )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction1, UNVERIFIED))

        whenever(retrievedTransaction1.id)
            .thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs)
            .thenReturn(listOf(StateRef(TX_ID_2, 0)))
        whenever(retrievedTransaction1.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction1.metadata)
            .thenReturn(tx1Metadata)

        // TX_ID_2 changed to VERIFIED here
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(eq(TX_ID_2), eq(UNVERIFIED)))
            .thenReturn(Pair(retrievedTransaction2, VERIFIED))

        whenever(retrievedTransaction2.id)
            .thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs)
            .thenReturn(emptyList())
        whenever(retrievedTransaction2.metadata)
            .thenReturn(tx1Metadata)

        whenever(utxoLedgerGroupParametersPersistenceService.find(groupParametersHash1))
            .thenReturn(groupParameters)

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1)).complete())
            .containsExactly(TX_ID_1) // TX_ID_2 will not be in the topological sort as it turned into VERIFIED

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionWithStatus(eq(TX_ID_1), eq(UNVERIFIED))

        verify(utxoLedgerPersistenceService, times(1))
            .findSignedTransactionWithStatus(eq(TX_ID_2), eq(UNVERIFIED))

        verify(session, never())
            .sendAndReceive(eq(List::class.java), any())
    }

    @Test
    fun `a resolved transaction has its dependencies retrieved from its peer and persisted`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2),
            listOf(retrievedTransaction3)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_2))
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2))
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)
        whenever(tx2Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction3.id).thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.metadata).thenReturn(tx1Metadata)
        whenever(tx3Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_3, TX_ID_2, TX_ID_1))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(
            session,
            times(3)
        ).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
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
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2),
            listOf(retrievedTransaction3)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_2))
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2))
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)
        whenever(tx2Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction3.id).thenReturn(TX_ID_3)
        whenever(retrievedTransaction3.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.referenceStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction3.metadata).thenReturn(tx1Metadata)
        whenever(tx3Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_3, TX_ID_2, TX_ID_1))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(
            session,
            times(3)
        ).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `receiving a transaction that is stored locally as VERIFIED does not have its dependencies added to the transactions to retrieve`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(TX_ID_1, VERIFIED))
            .thenReturn(Pair(retrievedTransaction1, VERIFIED))

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.VERIFIED)
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)
        whenever(tx2Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(listOf(TX_ID_2))

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session, never()).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(
            session,
            times(2)
        ).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `receiving only transactions that are stored locally as VERIFIED does not have their dependencies added to the transactions to retrieve and stops resolution`() {
        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.VERIFIED)
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction2.metadata).thenReturn(tx1Metadata)
        whenever(tx2Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)
        whenever(retrievedTransaction2.inputStateRefs).thenReturn(emptyList())
        whenever(retrievedTransaction2.referenceStateRefs).thenReturn(emptyList())

        assertThat(callTransactionBackchainReceiverFlow(setOf(TX_ID_1, TX_ID_2)).complete()).isEqualTo(emptyList<SecureHash>())

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_2)))
        verify(session, never()).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_3)))
        verify(
            session,
            times(2)
        ).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `receiving a transaction that was not included in the requested batch of transactions throws an exception`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(TX_ID_1, VERIFIED))
            .thenReturn(Pair(retrievedTransaction1, VERIFIED))

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(retrievedTransaction2.id).thenReturn(TX_ID_2)

        assertThatThrownBy { callTransactionBackchainReceiverFlow(setOf(TX_ID_1)) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
    }

    @Test
    fun `receiving signed group parameters that was not requested throws an exception`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(TX_ID_1, VERIFIED))
            .thenReturn(Pair(retrievedTransaction1, VERIFIED))

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(SecureHashImpl("SHA", byteArrayOf(103, 104, 105, 106)))

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        assertThatThrownBy { callTransactionBackchainReceiverFlow(setOf(TX_ID_1)) }
            .isExactlyInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("but received:")

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(eq(retrievedTransaction2), any())
    }

    @Test
    fun `receiving signed group parameters with invalid signature throws an exception`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(TX_ID_1, VERIFIED))
            .thenReturn(Pair(retrievedTransaction1, VERIFIED))

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)
        whenever(signedGroupParametersVerifier.verifySignature(any())).thenThrow(
            CryptoSignatureException("Invalid signature")
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.inputStateRefs).thenReturn(listOf(TX_3_INPUT_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.referenceStateRefs).thenReturn(listOf(TX_3_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1))
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        assertThatThrownBy { callTransactionBackchainReceiverFlow(setOf(TX_ID_1)) }
            .isExactlyInstanceOf(CryptoSignatureException::class.java)
            .hasMessageContaining("Invalid signature")

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(eq(retrievedTransaction2), any())
    }

    @Test
    fun `receiving a transaction without signed group parameters hash in its metadata throws an exception`() {
        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(TX_ID_1, VERIFIED))
            .thenReturn(Pair(retrievedTransaction1, VERIFIED))

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(retrievedTransaction1.id).thenReturn(TX_ID_1)
        whenever(retrievedTransaction1.metadata).thenReturn(tx1Metadata)
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(null)

        assertThatThrownBy { callTransactionBackchainReceiverFlow(setOf(TX_ID_1)) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)

        verify(session).sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(TX_ID_1)))
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(eq(retrievedTransaction1), any())
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
        val groupParameters4 = mock<SignedGroupParameters>()
        val groupParameters3 = mock<SignedGroupParameters>()
        val groupParameters2 = mock<SignedGroupParameters>()
        val groupParameters1 = mock<SignedGroupParameters>()

        val groupParametersHash4 = SecureHashImpl("SHA", byteArrayOf(104, 104, 104, 104))
        val groupParametersHash3 = SecureHashImpl("SHA", byteArrayOf(103, 103, 103, 103))
        val groupParametersHash2 = SecureHashImpl("SHA", byteArrayOf(102, 102, 102, 102))
        val groupParametersHash1 = SecureHashImpl("SHA", byteArrayOf(101, 101, 101, 101))

        whenever(groupParameters4.hash).thenReturn(groupParametersHash4)
        whenever(groupParameters3.hash).thenReturn(groupParametersHash3)
        whenever(groupParameters2.hash).thenReturn(groupParametersHash2)
        whenever(groupParameters1.hash).thenReturn(groupParametersHash1)

        val tx4Metadata = mock<TransactionMetadataInternal>()
        val tx3Metadata = mock<TransactionMetadataInternal>()
        val tx2Metadata = mock<TransactionMetadataInternal>()
        val tx1Metadata = mock<TransactionMetadataInternal>()

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
        whenever(transaction4.metadata).thenReturn(tx4Metadata)

        whenever(transaction3.id).thenReturn(transactionId3)
        whenever(transaction3.inputStateRefs).thenReturn(listOf(transaction1StateRef1))
        whenever(transaction3.metadata).thenReturn(tx3Metadata)

        whenever(transaction2.id).thenReturn(transactionId2)
        whenever(transaction2.inputStateRefs).thenReturn(listOf(transaction1StateRef0))
        whenever(transaction2.metadata).thenReturn(tx2Metadata)

        whenever(transaction1.id).thenReturn(transactionId1)
        whenever(transaction1.inputStateRefs).thenReturn(emptyList())
        whenever(transaction1.metadata).thenReturn(tx1Metadata)

        whenever(tx4Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash4.toString())
        whenever(tx3Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash3.toString())
        whenever(tx2Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash2.toString())
        whenever(tx1Metadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash1.toString())

        whenever(utxoLedgerPersistenceService.findSignedTransactionWithStatus(any(), any())).thenReturn(null)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(transaction3),
            listOf(transaction4),
            listOf(transaction1),
            listOf(transaction2)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters3,
            groupParameters4,
            groupParameters1,
            groupParameters2,
        )

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST)

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
            verify().sendAndReceive(
                SignedGroupParameters::class.java,
                TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash3)
            )
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId4)))
            verify().sendAndReceive(
                SignedGroupParameters::class.java,
                TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash4)
            )
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId1)))
            verify().sendAndReceive(
                SignedGroupParameters::class.java,
                TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1)
            )
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId2)))
            verify().sendAndReceive(
                SignedGroupParameters::class.java,
                TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash2)
            )
            Unit
        }

        utxoLedgerPersistenceService.inOrder {
            verify().persistIfDoesNotExist(transaction3, UNVERIFIED)
            verify().persistIfDoesNotExist(transaction4, UNVERIFIED)
            verify().persistIfDoesNotExist(transaction1, UNVERIFIED)
            verify().persistIfDoesNotExist(transaction2, UNVERIFIED)
            Unit
        }
    }

    private fun callTransactionBackchainReceiverFlow(originalTransactionsToRetrieve: Set<SecureHash>): TopologicalSort {
        return TransactionBackchainReceiverFlowV1(
            setOf(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))),
            originalTransactionsToRetrieve,
            session
        ).apply {
            utxoLedgerPersistenceService = this@TransactionBackchainReceiverFlowV1Test.utxoLedgerPersistenceService
            utxoLedgerMetricRecorder = this@TransactionBackchainReceiverFlowV1Test.utxoLedgerMetricRecorder
            utxoLedgerGroupParametersPersistenceService = this@TransactionBackchainReceiverFlowV1Test.utxoLedgerGroupParametersPersistenceService
            signedGroupParametersVerifier = this@TransactionBackchainReceiverFlowV1Test.signedGroupParametersVerifier
            flowConfigService = this@TransactionBackchainReceiverFlowV1Test.flowConfigService
        }.call()
    }
}
