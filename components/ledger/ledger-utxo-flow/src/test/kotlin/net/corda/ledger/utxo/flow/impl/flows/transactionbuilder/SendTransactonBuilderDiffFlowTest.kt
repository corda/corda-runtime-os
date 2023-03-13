package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant

@Suppress("MaxLineLength")
class SendTransactonBuilderDiffFlowTest {
    private val transactionBuilder = mock<UtxoTransactionBuilderInternal>()
    private val originalTransactionalBuilder = mock<UtxoTransactionBuilderInternal>()
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
        whenever(flowEngine.subFlow(any<TransactionBackchainSenderFlow>())).thenReturn(Unit)

        whenever(transactionBuilder.notary).thenReturn(null)
        whenever(transactionBuilder.timeWindow).thenReturn(null)
        whenever(transactionBuilder.attachments).thenReturn(listOf())
        whenever(transactionBuilder.commands).thenReturn(listOf())
        whenever(transactionBuilder.signatories).thenReturn(listOf())
        whenever(transactionBuilder.inputStateRefs).thenReturn(listOf())
        whenever(transactionBuilder.referenceStateRefs).thenReturn(listOf())
        whenever(transactionBuilder.outputStates).thenReturn(listOf())

        whenever(originalTransactionalBuilder.notary).thenReturn(null)
        whenever(originalTransactionalBuilder.timeWindow).thenReturn(null)
        whenever(originalTransactionalBuilder.attachments).thenReturn(listOf())
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf())
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf())
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf())
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf())
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf())
    }

    @Test
    fun `called with empty builders sends back an empty builder`() {
        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with old notary null and new notary sends back a builder with the new notary`() {
        whenever(transactionBuilder.notary).thenReturn(utxoNotaryExample)

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(notary = utxoNotaryExample))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with old notary and a different new notary sends back a builder without notary`() {
        val alternativeNotary = Party(
            MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB"),
            anotherPublicKey
        )
        whenever(originalTransactionalBuilder.notary).thenReturn(alternativeNotary)
        whenever(transactionBuilder.notary).thenReturn(utxoNotaryExample)

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with old time window null and new time window sends back a builder with the new time window`() {
        whenever(transactionBuilder.timeWindow).thenReturn(utxoTimeWindowExample)

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with old time window and a different new time window sends back a builder without timewindow`() {
        whenever(originalTransactionalBuilder.timeWindow).thenReturn(TimeWindowUntilImpl(Instant.now()))
        whenever(transactionBuilder.timeWindow).thenReturn(utxoTimeWindowExample)

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same attachments does not send anything`() {
        whenever(originalTransactionalBuilder.attachments).thenReturn(listOf(hash1))
        whenever(transactionBuilder.attachments).thenReturn(listOf(hash1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same attachments duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.attachments).thenReturn(listOf(hash1))
        whenever(transactionBuilder.attachments).thenReturn(listOf(hash1, hash1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with a new attachments returns the new only`() {
        whenever(originalTransactionalBuilder.attachments).thenReturn(listOf(hash1))
        whenever(transactionBuilder.attachments).thenReturn(listOf(hash1, hash2))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(attachments = mutableListOf(hash2)))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same commands does not send anything`() {
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf(command1))
        whenever(transactionBuilder.commands).thenReturn(listOf(command1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same commands duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf(command1))
        whenever(transactionBuilder.commands).thenReturn(listOf(command1, command1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with a new commands returns the new only`() {
        whenever(originalTransactionalBuilder.commands).thenReturn(listOf(command1))
        whenever(transactionBuilder.commands).thenReturn(listOf(command1, command2))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(commands = mutableListOf(command2)))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same signatories does not send anything`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(transactionBuilder.signatories).thenReturn(listOf(publicKeyExample))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same signatories duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(transactionBuilder.signatories).thenReturn(listOf(publicKeyExample, publicKeyExample))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with a new signatories returns the new only`() {
        whenever(originalTransactionalBuilder.signatories).thenReturn(listOf(publicKeyExample))
        whenever(transactionBuilder.signatories).thenReturn(listOf(publicKeyExample, anotherPublicKey))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(signatories = mutableListOf(anotherPublicKey)))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same input state refs does not send anything`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(transactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same input state refs duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(transactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1, stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with a new input state refs returns the new only`() {
        whenever(originalTransactionalBuilder.inputStateRefs).thenReturn(listOf(stateRef1))
        whenever(transactionBuilder.inputStateRefs).thenReturn(listOf(stateRef1, stateRef2))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef2)))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(stateRef2.transactionId), session))
    }

    @Test
    fun `called with the same reference state refs does not send anything`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(transactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same reference state refs duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(transactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef1, stateRef1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with a new reference state refs returns the new only`() {
        whenever(originalTransactionalBuilder.referenceStateRefs).thenReturn(listOf(stateRef1))
        whenever(transactionBuilder.referenceStateRefs).thenReturn(listOf(stateRef1, stateRef2))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef2)))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(stateRef2.transactionId), session))
    }

    @Test
    fun `called with the same outputs does not send anything`() {
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf(state1))
        whenever(transactionBuilder.outputStates).thenReturn(listOf(state1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with the same outputs duplicated does not send anything`() {
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf(state1))
        whenever(transactionBuilder.outputStates).thenReturn(listOf(state1, state1))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer())
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    @Test
    fun `called with a new outputs returns the new only`() {
        whenever(originalTransactionalBuilder.outputStates).thenReturn(listOf(state1))
        whenever(transactionBuilder.outputStates).thenReturn(listOf(state1, state2))

        callSendFlow()

        verify(session).send(UtxoTransactionBuilderContainer(outputStates = mutableListOf(state2)))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(setOf(), session))
    }

    private fun callSendFlow() {
        val flow = SendTransactionBuilderDiffFlow(
            transactionBuilder,
            session,
            originalTransactionalBuilder
        )

        flow.flowEngine = flowEngine
        flow.call()
    }
}
