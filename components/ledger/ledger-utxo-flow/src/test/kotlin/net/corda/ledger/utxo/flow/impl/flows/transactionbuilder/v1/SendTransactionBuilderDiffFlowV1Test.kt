package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.flow.impl.transaction.ContractStateAndEncumbranceTag
import net.corda.ledger.utxo.flow.impl.transaction.UtxoBaselinedTransactionBuilder
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

@Suppress("MaxLineLength")
class SendTransactionBuilderDiffFlowV1Test {
    private val currentTransactionBuilder = mock<UtxoTransactionBuilderInternal>()
    private val originalTransactionalBuilder = mock<UtxoTransactionBuilderContainer>()
    private val session = mock<FlowSession>()
    private val hash1 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 2)), 0)
    private val state1 = mock<ContractStateAndEncumbranceTag>()
    private val state2 = mock<ContractStateAndEncumbranceTag>()

    private val flowEngine = mock<FlowEngine>()
    private val notaryLookup = mock<NotaryLookup>()
    private val utxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()

    private val notaryKey = mock<PublicKey>()
    private val notaryInfo = mock<NotaryInfo> {
        on { isBackchainRequired } doReturn true
        on { publicKey } doReturn notaryKey
        on { name } doReturn notaryX500Name
    }

    @BeforeEach
    fun beforeEach() {
        whenever(flowEngine.subFlow(any<TransactionBackchainSenderFlow>())).thenReturn(Unit)

        whenever(notaryLookup.lookup(eq(notaryX500Name))).thenReturn(notaryInfo)

        whenever(currentTransactionBuilder.notaryName).thenReturn(notaryX500Name)
        whenever(currentTransactionBuilder.notaryKey).thenReturn(null)
        whenever(currentTransactionBuilder.timeWindow).thenReturn(null)
        whenever(currentTransactionBuilder.commands).thenReturn(listOf())
        whenever(currentTransactionBuilder.signatories).thenReturn(listOf())
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf())
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf())
        whenever(currentTransactionBuilder.outputStates).thenReturn(listOf())
        whenever(currentTransactionBuilder.copy()).thenReturn(originalTransactionalBuilder)

        whenever(originalTransactionalBuilder.getNotaryName()).thenReturn(null)
        whenever(originalTransactionalBuilder.timeWindow).thenReturn(null)
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf())
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf())
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf())
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf())
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf())
    }

    @Test
    fun `called with empty builders sends back an empty builder`() {
        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with old notary null and new notary sends back a builder with the new notary`() {
        whenever(currentTransactionBuilder.notaryName).thenReturn(notaryX500Name)
        whenever(currentTransactionBuilder.notaryKey).thenReturn(publicKeyExample)

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with old notary and a different new notary sends back a builder without notary`() {
        whenever(originalTransactionalBuilder.getNotaryName()).thenReturn(anotherNotaryX500Name)
        whenever(currentTransactionBuilder.notaryName).thenReturn(notaryX500Name)
        whenever(currentTransactionBuilder.notaryKey).thenReturn(publicKeyExample)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("Notary name on transaction builder must not be null")
    }

    @Test
    fun `called with old time window null and new time window sends back a builder with the new time window`() {
        whenever(currentTransactionBuilder.timeWindow).thenReturn(utxoTimeWindowExample)

        callSendFlow()

        verify(session).send(
            UtxoTransactionBuilderContainer(
                timeWindow = utxoTimeWindowExample,
                notaryName = notaryX500Name
            )
        )
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with old time window and a different new time window sends back a builder without timewindow`() {
        whenever(originalTransactionalBuilder.timeWindow).thenReturn(TimeWindowUntilImpl(Instant.now()))
        whenever(currentTransactionBuilder.timeWindow).thenReturn(utxoTimeWindowExample)

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with the same commands does not send anything`() {
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf(command1))
        whenever(currentTransactionBuilder.commands).thenReturn(listOf(command1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with a new commands returns the new ones`() {
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf(command1))
        whenever(currentTransactionBuilder.commands).thenReturn(listOf(command1, command1, command2))

        callSendFlow()

        verify(session).send(
            UtxoTransactionBuilderContainer(
                commands = mutableListOf(command1, command2),
                notaryName = notaryX500Name
            )
        )
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with the same signatories does not send anything`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(currentTransactionBuilder.signatories).thenReturn(listOf(publicKeyExample))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with the same signatories duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(currentTransactionBuilder.signatories).thenReturn(listOf(publicKeyExample, publicKeyExample))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with a new signatories returns the new only`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(currentTransactionBuilder.signatories).thenReturn(listOf(publicKeyExample, anotherPublicKeyExample))

        callSendFlow()

        verify(session).send(
            UtxoTransactionBuilderContainer(
                signatories = mutableListOf(anotherPublicKeyExample),
                notaryName = notaryX500Name
            )
        )
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with the same input state refs does not send anything`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with the same input state refs duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1, stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with a new input state refs returns the new only`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1, stateRef2))

        callSendFlow()

        verify(session).send(
            UtxoTransactionBuilderContainer(
                inputStateRefs = mutableListOf(stateRef2),
                notaryName = notaryX500Name
            )
        )
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(stateRef2.transactionId), session))
    }

    @Test
    fun `called with the same reference state refs does not send anything`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with the same reference state refs duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef1, stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with a new reference state refs returns the new only`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef1, stateRef2))

        callSendFlow()

        verify(session).send(
            UtxoTransactionBuilderContainer(
                referenceStateRefs = mutableListOf(stateRef2),
                notaryName = notaryX500Name
            )
        )
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(stateRef2.transactionId), session))
    }

    @Test
    fun `called with the same outputs does not send anything`() {
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf(state1))
        whenever(currentTransactionBuilder.outputStates).thenReturn(listOf(state1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `called with a new outputs returns the new ones`() {
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf(state1))
        whenever(currentTransactionBuilder.outputStates).thenReturn(listOf(state1, state1, state2))

        callSendFlow()

        verify(session).send(
            UtxoTransactionBuilderContainer(
                outputStates = mutableListOf(state1, state2),
                notaryName = notaryX500Name
            )
        )
        verify(flowEngine, never()).subFlow(any<TransactionBackchainSenderFlow>())
    }

    @Test
    fun `notary backchain off - should result in sending filtered transactions`() {
        val filteredTransactionsAndSignatures = mapOf<SecureHash, UtxoFilteredTransactionAndSignatures>(
            hash1 to mock(),
            hash2 to mock()
        )

        val mockNotaryInfo = mock<NotaryInfo> {
            on { isBackchainRequired } doReturn false
            on { publicKey } doReturn notaryKey
            on { name } doReturn notaryX500Name
        }

        whenever(notaryLookup.lookup(eq(notaryX500Name))).thenReturn(mockNotaryInfo)

        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(currentTransactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef2))

        whenever(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(
                eq(currentTransactionBuilder.inputStateRefs + currentTransactionBuilder.referenceStateRefs),
                eq(notaryKey),
                eq(notaryX500Name)
            )
        ).thenReturn(filteredTransactionsAndSignatures)

        callSendFlow()

        verify(session).send(
            UtxoTransactionBuilderContainer(
                inputStateRefs = listOf(stateRef1),
                referenceStateRefs = listOf(stateRef2),
                filteredDependencies = filteredTransactionsAndSignatures.values.toList(),
                notaryName = notaryX500Name
            )
        )
    }

    @Test
    fun `notary backchain off - mismatch in the number of dependencies and filtered transactions`() {
        val mockNotaryInfo = mock<NotaryInfo> {
            on { isBackchainRequired } doReturn false
            on { publicKey } doReturn notaryKey
            on { name } doReturn notaryX500Name
        }

        whenever(notaryLookup.lookup(eq(notaryX500Name))).thenReturn(mockNotaryInfo)

        whenever(currentTransactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))

        whenever(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(
                eq(originalTransactionalBuilder.inputStateRefs + originalTransactionalBuilder.referenceStateRefs),
                eq(notaryKey),
                eq(notaryX500Name)
            )
        ).thenReturn(emptyMap())

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("The number of filtered transactions didn't match the number of dependencies.")
    }

    private fun callSendFlow() {
        val flow = SendTransactionBuilderDiffFlowV1(
            UtxoBaselinedTransactionBuilder(currentTransactionBuilder).diff(),
            session
        ).also {
            it.notaryLookup = notaryLookup
            it.persistenceService = utxoLedgerPersistenceService
        }

        flow.flowEngine = flowEngine
        flow.call()
    }
}
