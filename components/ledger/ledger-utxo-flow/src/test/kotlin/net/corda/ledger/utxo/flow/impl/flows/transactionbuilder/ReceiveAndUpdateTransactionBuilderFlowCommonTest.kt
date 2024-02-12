package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.flow.impl.transaction.ContractStateAndEncumbranceTag
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

abstract class ReceiveAndUpdateTransactionBuilderFlowCommonTest : UtxoLedgerTest() {

    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val stateWithEnc1 = ContractStateAndEncumbranceTag(state1, null)
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val stateWithEnc2 = ContractStateAndEncumbranceTag(state2, null)

    protected val mockFlowEngine = mock<FlowEngine>()
    protected val stateRef1 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    protected val stateRef2 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 2, 2)), 0)
    protected val session = mock<FlowSession>()

    protected lateinit var originalTransactionalBuilder: UtxoTransactionBuilder

    @Test
    fun `called with empty builder and receiving empty returns an empty builder`() {
        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer()
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoLedgerService.createTransactionBuilder(), returnedTransactionBuilder)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary null and receives new notary returns a builder with the new notary`() {
        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(notaryName = notaryX500Name)
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(notaryX500Name, returnedTransactionBuilder.notaryName)
        assertEquals(publicKeyExample, returnedTransactionBuilder.notaryKey)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary and receives a different new notary returns with the original notary`() {
        originalTransactionalBuilder.setNotary(notaryX500Name)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(notaryName = anotherNotaryX500Name)
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(notaryX500Name, returnedTransactionBuilder.notaryName)
        assertEquals(publicKeyExample, returnedTransactionBuilder.notaryKey)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original time window null and receives new time window returns a builder with the new time window`() {
        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample)
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `Called with original time window and receives a different new time window returns with the original time window`() {
        originalTransactionalBuilder.setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(timeWindow = TimeWindowUntilImpl(Instant.MAX))
        )

        val returnedTransactionBuilder = callSendFlow()
        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving commands appends them (new, old, duplicated)`() {
        originalTransactionalBuilder.addCommand(command1)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(commands = mutableListOf(command1, command1, command2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(command1, command1, command1, command2), returnedTransactionBuilder.commands)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new signatories appends them`() {
        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(signatories = mutableListOf(publicKeyExample, anotherPublicKeyExample))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing signatories does not append it`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(signatories = mutableListOf(publicKeyExample))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated signatories appends once`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(
                signatories = mutableListOf(
                    publicKeyExample,
                    anotherPublicKeyExample,
                    anotherPublicKeyExample
                )
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new input StateRefs appends them`() {
        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.inputStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ),
                session
            )
        )
    }

    @Test
    fun `receiving existing input StateRefs does not append it`() {
        originalTransactionalBuilder.addInputState(stateRef1)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1), returnedTransactionBuilder.inputStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                ),
                session
            )
        )
    }

    @Test
    fun `receiving duplicated input StateRefs appends once`() {
        originalTransactionalBuilder.addInputState(stateRef1)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.inputStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ),
                session
            )
        )
    }

    @Test
    fun `receiving new reference StateRefs appends them`() {
        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.referenceStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ),
                session
            )
        )
    }

    @Test
    fun `receiving existing reference StateRefs does not append it`() {
        originalTransactionalBuilder.addReferenceState(stateRef1)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1), returnedTransactionBuilder.referenceStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                ),
                session
            )
        )
    }

    @Test
    fun `receiving duplicated reference StateRefs appends once`() {
        originalTransactionalBuilder.addReferenceState(stateRef1)

        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(stateRef1, stateRef2), returnedTransactionBuilder.referenceStateRefs)
        verify(mockFlowEngine).subFlow(
            TransactionBackchainResolutionFlow(
                setOf(
                    stateRef1.transactionId,
                    stateRef2.transactionId
                ),
                session
            )
        )
    }

    @Test
    fun `receiving outputs appends them (new, old, duplicated)`() {
        originalTransactionalBuilder.addOutputState(state1)
        session.mockReceiveWrapperOrBuilder(
            UtxoTransactionBuilderContainer(outputStates = mutableListOf(stateWithEnc1, stateWithEnc1, stateWithEnc2))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(
            listOf(stateWithEnc1, stateWithEnc1, stateWithEnc1, stateWithEnc2),
            returnedTransactionBuilder.outputStates
        )
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    abstract val payloadWrapper: Class<*>?

    abstract fun callSendFlow(): UtxoTransactionBuilderInternal

    private fun FlowSession.mockReceiveWrapperOrBuilder(builder: UtxoTransactionBuilderContainer) {
        whenever(this.receive(payloadWrapper ?: UtxoTransactionBuilderContainer::class.java)).thenReturn(
            payloadWrapper?.getConstructor(UtxoTransactionBuilderContainer::class.java)?.newInstance(builder) ?: builder
        )
    }
}
