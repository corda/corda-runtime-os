package net.corda.ledger.utxo.flow.impl.flows.backchain.v2

import net.corda.crypto.core.SecureHashImpl
import net.corda.flow.application.services.FlowConfigService
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.utxo.flow.impl.UtxoLedgerMetricRecorder
import net.corda.ledger.utxo.flow.impl.flows.backchain.TopologicalSort
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackChainResolutionVersion
import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainReceiverFlowV1
import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainRequestV1
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
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
import org.mockito.Mockito.times
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
class TransactionBackchainReceiverFlowV2Test {

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

    private val groupParameters = mock<SignedGroupParameters>()
    private val groupParametersHash1 = SecureHashImpl("SHA", byteArrayOf(101, 101, 101, 101))

    private val tx1Metadata = mock<TransactionMetadataInternal>()
    private val tx2Metadata = mock<TransactionMetadataInternal>()
    private val tx3Metadata = mock<TransactionMetadataInternal>()

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

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

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
        verify(session, times(3)).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
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
        whenever(utxoLedgerPersistenceService.findSignedTransaction(any(), any())).thenReturn(null)

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
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

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
        verify(session, times(3)).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `receiving a transaction that is stored locally as VERIFIED does not have its dependencies added to the transactions to retrieve`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(any(), eq(UNVERIFIED)))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.VERIFIED to listOf(PACKAGE_SUMMARY))
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
        verify(session, times(2)).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
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
            .thenReturn(TransactionExistenceStatus.VERIFIED to listOf(PACKAGE_SUMMARY))
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
        verify(session, times(2)).sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
        verify(session).send(TransactionBackchainRequestV1.Stop)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED)
        verify(utxoLedgerPersistenceService).persistIfDoesNotExist(retrievedTransaction2, UNVERIFIED)
        verify(utxoLedgerPersistenceService, never()).persistIfDoesNotExist(retrievedTransaction3, UNVERIFIED)
    }

    @Test
    fun `receiving a transaction that was not included in the requested batch of transactions throws an exception`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
            listOf(retrievedTransaction2)
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(groupParametersHash1)

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

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
    fun `receiving signed group parameters that was not requested  throws an exception`() {
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

        whenever(session.sendAndReceive(eq(List::class.java), any())).thenReturn(
            listOf(retrievedTransaction1),
        )

        whenever(session.sendAndReceive(eq(SignedGroupParameters::class.java), any())).thenReturn(
            groupParameters,
        )
        whenever(groupParameters.hash).thenReturn(SecureHashImpl("SHA", byteArrayOf(103, 104, 105, 106)))

        whenever(utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction1, UNVERIFIED))
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

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
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

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
            .thenReturn(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(PACKAGE_SUMMARY))

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
        whenever(utxoLedgerPersistenceService.findSignedTransaction(TX_ID_1)).thenReturn(retrievedTransaction1)

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

        whenever(utxoLedgerPersistenceService.findSignedTransaction(any(), any())).thenReturn(null)

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
            verify().sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash3))
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId4)))
            verify().sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash4))
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId1)))
            verify().sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash1))
            verify().sendAndReceive(List::class.java, TransactionBackchainRequestV1.Get(setOf(transactionId2)))
            verify().sendAndReceive(SignedGroupParameters::class.java, TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash2))
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
            originalTransactionsToRetrieve, session, TransactionBackChainResolutionVersion.V2
        ).apply {
            utxoLedgerPersistenceService = this@TransactionBackchainReceiverFlowV2Test.utxoLedgerPersistenceService
            utxoLedgerMetricRecorder = this@TransactionBackchainReceiverFlowV2Test.utxoLedgerMetricRecorder
            utxoLedgerGroupParametersPersistenceService = this@TransactionBackchainReceiverFlowV2Test.utxoLedgerGroupParametersPersistenceService
            signedGroupParametersVerifier = this@TransactionBackchainReceiverFlowV2Test.signedGroupParametersVerifier
            flowConfigService = this@TransactionBackchainReceiverFlowV2Test.flowConfigService
        }.call()
    }
}
