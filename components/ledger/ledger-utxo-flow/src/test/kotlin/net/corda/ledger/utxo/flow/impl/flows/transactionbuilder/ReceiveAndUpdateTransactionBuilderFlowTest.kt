package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.transaction.ContractStateAndEncumbranceTag
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Suppress("MaxLineLength")
class ReceiveAndUpdateTransactionBuilderFlowTest : UtxoLedgerTest() {
    private lateinit var originalTransactionalBuilder: UtxoTransactionBuilder
    private val session = mock<FlowSession>()

    private val hash1 = SecureHash("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHash("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 2, 2)), 0)
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val stateWithEnc1 = ContractStateAndEncumbranceTag(state1, null)
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val stateWithEnc2 = ContractStateAndEncumbranceTag(state2, null)

    private val anotherPublicKey = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair().public

    private val mockFlowEngine = mock<FlowEngine>()

    @BeforeEach
    fun beforeEach() {
        whenever(mockFlowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        originalTransactionalBuilder = utxoLedgerService.transactionBuilder
    }

    @Test
    fun `called with empty builder and receiving empty returns an empty builder`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer()
        )
        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoLedgerService.transactionBuilder, returnedTransactionBuilder)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary null and receives new notary returns a builder with the new notary`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(notary = utxoNotaryExample)
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoNotaryExample, returnedTransactionBuilder.notary)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary and receives a different new notary returns with the original notary`() {
        val alternativeNotary = Party(
            MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB"),
            anotherPublicKey
        )
        originalTransactionalBuilder.setNotary(utxoNotaryExample)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(notary = alternativeNotary)
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoNotaryExample, returnedTransactionBuilder.notary)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original time window null and receives new time window return a builder with the new time window`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample)
        )
        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `Called with original time window and receives a different new time window returns with the original time window`() {
        originalTransactionalBuilder.setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample)
        )

        val returnedTransactionBuilder = callSendFlow()
        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new attachments appends it`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(attachments = listOf(hash1, hash2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(hash1, hash2), returnedTransactionBuilder.attachments)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing attachment does not append it`() {
        originalTransactionalBuilder.addAttachment(hash1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(attachments = mutableListOf(hash1))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(hash1), returnedTransactionBuilder.attachments)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated attachments appends once`() {
        originalTransactionalBuilder.addAttachment(hash1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(attachments = mutableListOf(hash1, hash2, hash2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(hash1, hash2), returnedTransactionBuilder.attachments)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new commands appends it`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(commands = mutableListOf(command1, command2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(command1, command2), returnedTransactionBuilder.commands)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing commands does not append it`() {
        originalTransactionalBuilder.addCommand(command1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(commands = mutableListOf(command1))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(command1), returnedTransactionBuilder.commands)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated commands appends once`() {
        originalTransactionalBuilder.addCommand(command1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(commands = mutableListOf(command1, command2, command2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(command1, command2), returnedTransactionBuilder.commands)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new signatories appends it`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(signatories = mutableListOf(publicKeyExample, anotherPublicKey))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKey), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing signatories does not append it`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(signatories = mutableListOf(publicKeyExample))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated signatories appends once`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                signatories = mutableListOf(
                    publicKeyExample,
                    anotherPublicKey,
                    anotherPublicKey
                )
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKey), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new input StateRefs appends it`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.inputStateRefs)
        verify(mockFlowEngine).subFlow(
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
        originalTransactionalBuilder.addInputState(stateRef1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1), returnedTransactionBuilder.inputStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                ), session
            )
        )
    }

    @Test
    fun `receiving duplicated input StateRefs appends once`() {
        originalTransactionalBuilder.addInputState(stateRef1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.inputStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ), session
            )
        )
    }

    @Test
    fun `receiving new reference StateRefs appends it`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.referenceStateRefs)
        verify(mockFlowEngine).subFlow(
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
        originalTransactionalBuilder.addReferenceState(stateRef1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1), returnedTransactionBuilder.referenceStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                ), session
            )
        )
    }

    @Test
    fun `receiving duplicated reference StateRefs appends once`() {
        originalTransactionalBuilder.addReferenceState(stateRef1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.referenceStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ), session
            )
        )
    }

    @Test
    fun `receiving new outputs appends it`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(outputStates = mutableListOf(stateWithEnc1, stateWithEnc2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateWithEnc1, stateWithEnc2), returnedTransactionBuilder.outputStates)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing outputs does not append it`() {
        originalTransactionalBuilder.addOutputState(state1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(outputStates = mutableListOf(stateWithEnc1))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateWithEnc1), returnedTransactionBuilder.outputStates)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated outputs appends once`() {
        originalTransactionalBuilder.addOutputState(state1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(outputStates = mutableListOf(stateWithEnc1, stateWithEnc1, stateWithEnc2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateWithEnc1, stateWithEnc2), returnedTransactionBuilder.outputStates)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    private fun callSendFlow(): UtxoTransactionBuilderInternal {
        val flow = ReceiveAndUpdateTransactionBuilderFlow(
            session,
            originalTransactionalBuilder as UtxoTransactionBuilderInternal
        )

        flow.flowEngine = mockFlowEngine
        return flow.call() as UtxoTransactionBuilderInternal
    }
}
