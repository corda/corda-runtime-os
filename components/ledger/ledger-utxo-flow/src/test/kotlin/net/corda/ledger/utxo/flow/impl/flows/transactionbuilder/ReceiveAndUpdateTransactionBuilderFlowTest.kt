package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.flow.impl.transaction.ContractStateAndEncumbranceTag
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlin.test.assertEquals

@Suppress("MaxLineLength")
class ReceiveAndUpdateTransactionBuilderFlowTest {
    private val originalTransactionalBuilder = mock<UtxoTransactionBuilderInternal>()
    private val receivedTransactionBuilder = mock<UtxoTransactionBuilderContainer>()
    private val expectedTransactionBuilder = mock<UtxoTransactionBuilderInternal>()
    private val session = mock<FlowSession>()

    private val hash1 = SecureHash("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHash("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 1, 2)), 0)
    private val state1 = mock<ContractStateAndEncumbranceTag>()
    private val state2 = mock<ContractStateAndEncumbranceTag>()

    private val anotherPublicKey = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair().public

    private val flowEngine = mock<FlowEngine>()

    @BeforeEach
    fun beforeEach() {
        whenever(flowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(receivedTransactionBuilder)

        whenever(originalTransactionalBuilder.notary).thenReturn(null)
        whenever(originalTransactionalBuilder.timeWindow).thenReturn(null)
        whenever(originalTransactionalBuilder.attachments).thenReturn(mutableListOf())
        whenever(originalTransactionalBuilder.commands).thenReturn(mutableListOf())
        whenever(originalTransactionalBuilder.signatories).thenReturn(mutableListOf())
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(mutableListOf())
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(mutableListOf())
        whenever(originalTransactionalBuilder.outputStates).thenReturn(mutableListOf())
        whenever(originalTransactionalBuilder.copy()).thenReturn(expectedTransactionBuilder)

        whenever(receivedTransactionBuilder.notary).thenReturn(null)
        whenever(receivedTransactionBuilder.timeWindow).thenReturn(null)
        whenever(receivedTransactionBuilder.attachments).thenReturn(mutableListOf())
        whenever(receivedTransactionBuilder.commands).thenReturn(mutableListOf())
        whenever(receivedTransactionBuilder.signatories).thenReturn(mutableListOf())
        whenever(receivedTransactionBuilder.inputStateRefs).thenReturn(mutableListOf())
        whenever(receivedTransactionBuilder.referenceStateRefs).thenReturn(mutableListOf())
        whenever(receivedTransactionBuilder.outputStates).thenReturn(mutableListOf())

        whenever(state1.contractState).thenReturn(mock())
        whenever(state2.contractState).thenReturn(mock())
    }

    @Test
    fun `called with empty builder and receiving nothing returns an empty builder`() {
        val returnedTransactionBuilder = callSendFlow()

        assertEquals(expectedTransactionBuilder, returnedTransactionBuilder)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary null and receives new notary returns a builder with the new notary`() {
        whenever(receivedTransactionBuilder.notary).thenReturn(utxoNotaryExample)

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).setNotary(utxoNotaryExample)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary and receives a different new notary returns with the original notary`() {
        val alternativeNotary = Party(
            MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB"),
            anotherPublicKey
        )
        whenever(originalTransactionalBuilder.notary).thenReturn(utxoNotaryExample)
        whenever(receivedTransactionBuilder.notary).thenReturn(alternativeNotary)

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder, never()).setNotary(any())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original time window null and receives new time window return a builder with the new time window`() {
        whenever(receivedTransactionBuilder.timeWindow).thenReturn(utxoTimeWindowExample)

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `Called with original time window and receives a different new time window returns with the original time window`() {
        whenever(originalTransactionalBuilder.timeWindow).thenReturn(utxoTimeWindowExample)
        whenever(receivedTransactionBuilder.timeWindow).thenReturn(TimeWindowUntilImpl(Instant.now()))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder, never()).setTimeWindowBetween(any(), any())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new attachments appends it`() {
        whenever(receivedTransactionBuilder.attachments).thenReturn(mutableListOf(hash1, hash2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addAttachment(hash1)
        verify(returnedTransactionBuilder).addAttachment(hash2)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing attachment does not append it`() {
        whenever(originalTransactionalBuilder.attachments).thenReturn(listOf(hash1))
        whenever(receivedTransactionBuilder.attachments).thenReturn(mutableListOf(hash1))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder, never()).addAttachment(any())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated attachments appends once`() {
        whenever(originalTransactionalBuilder.attachments).thenReturn(listOf(hash1))
        whenever(receivedTransactionBuilder.attachments).thenReturn(mutableListOf(hash1, hash2, hash2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addAttachment(hash2)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new commands appends it`() {
        whenever(receivedTransactionBuilder.commands).thenReturn(mutableListOf(command1, command2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addCommand(command1)
        verify(returnedTransactionBuilder).addCommand(command2)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing commands does not append it`() {
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf(command1))
        whenever(receivedTransactionBuilder.commands).thenReturn(mutableListOf(command1))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder, never()).addCommand(any())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated commands appends once`() {
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf(command1))
        whenever(receivedTransactionBuilder.commands).thenReturn(mutableListOf(command1, command2, command2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addCommand(command2)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new signatories appends it`() {
        whenever(receivedTransactionBuilder.signatories).thenReturn(mutableListOf(publicKeyExample, anotherPublicKey))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addSignatories(listOf(publicKeyExample, anotherPublicKey))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing signatories does not append it`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(receivedTransactionBuilder.signatories).thenReturn(mutableListOf(publicKeyExample))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addSignatories(listOf())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated signatories appends once`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(receivedTransactionBuilder.signatories).thenReturn(mutableListOf(publicKeyExample, anotherPublicKey, anotherPublicKey))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addSignatories(listOf(anotherPublicKey))
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new input StateRefs appends it`() {
        whenever(receivedTransactionBuilder.inputStateRefs).thenReturn(mutableListOf(stateRef1, stateRef2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addInputStates(listOf(stateRef1, stateRef2))
        verify(flowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ), session
            )
        )
    }

    @Test
    fun `receiving existing input StateRefs does not append it`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(receivedTransactionBuilder.inputStateRefs).thenReturn(mutableListOf(stateRef1))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addInputStates(listOf())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated input StateRefs appends once`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(receivedTransactionBuilder.inputStateRefs).thenReturn(mutableListOf(stateRef1, stateRef2, stateRef2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addInputStates(listOf(stateRef2))
        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(setOf(stateRef2.transactionId), session))
    }

    @Test
    fun `receiving new reference StateRefs appends it`() {
        whenever(receivedTransactionBuilder.referenceStateRefs).thenReturn(mutableListOf(stateRef1, stateRef2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addReferenceStates(listOf(stateRef1, stateRef2))
        verify(flowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ), session
            )
        )
    }

    @Test
    fun `receiving existing reference StateRefs does not append it`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(receivedTransactionBuilder.referenceStateRefs).thenReturn(mutableListOf(stateRef1))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addReferenceStates(listOf())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated reference StateRefs appends once`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(receivedTransactionBuilder.referenceStateRefs).thenReturn(mutableListOf(stateRef1, stateRef2, stateRef2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addReferenceStates(listOf(stateRef2))
        verify(flowEngine).subFlow(TransactionBackchainResolutionFlow(setOf(stateRef2.transactionId), session))
    }

    @Test
    fun `receiving new outputs appends it`() {
        whenever(receivedTransactionBuilder.outputStates).thenReturn(mutableListOf(state1, state2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addOutputState(state1.contractState)
        verify(returnedTransactionBuilder).addOutputState(state2.contractState)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing outputs does not append it`() {
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf(state1))
        whenever(receivedTransactionBuilder.outputStates).thenReturn(mutableListOf(state1))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder, never()).addOutputState(any())
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated outputs appends once`() {
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf(state1))
        whenever(receivedTransactionBuilder.outputStates).thenReturn(mutableListOf(state1, state2, state2))

        val returnedTransactionBuilder = callSendFlow()

        verify(returnedTransactionBuilder).addOutputState(state2.contractState)
        verify(flowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    private fun callSendFlow(): UtxoTransactionBuilderInternal {
        val flow = ReceiveAndUpdateTransactionBuilderFlow(
            session,
            originalTransactionalBuilder
        )

        flow.flowEngine = flowEngine
        return flow.call() as UtxoTransactionBuilderInternal
    }
}
