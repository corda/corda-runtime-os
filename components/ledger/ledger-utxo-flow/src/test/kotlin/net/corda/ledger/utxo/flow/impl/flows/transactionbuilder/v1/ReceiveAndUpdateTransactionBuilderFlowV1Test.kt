package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
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
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.GroupParametersLookup
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Suppress("MaxLineLength")
class ReceiveAndUpdateTransactionBuilderFlowV1Test : UtxoLedgerTest() {
    private lateinit var originalTransactionalBuilder: UtxoTransactionBuilder
    private val session = mock<FlowSession>()

    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 2, 2)), 0)
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val stateWithEnc1 = ContractStateAndEncumbranceTag(state1, null)
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val stateWithEnc2 = ContractStateAndEncumbranceTag(state2, null)

    private val mockFlowEngine = mock<FlowEngine>()

    private val notaryKey = mock<PublicKey>()

    private val notaryInfo = mock<NotaryInfo> {
        on { name } doReturn notaryX500Name
        on { publicKey } doReturn notaryKey
    }

    private val filteredTransaction = mock<UtxoFilteredTransaction> {
        on { verify() } doAnswer { }
        on { notaryName } doReturn notaryX500Name
    }
    private val invalidFilteredTransaction = mock<UtxoFilteredTransaction> {
        on { verify() } doAnswer { throw IllegalArgumentException("Couldn't verify transaction!") }
    }

    private val groupParameters = mock<GroupParameters> {
        on { notaries } doReturn listOf(notaryInfo)
    }

    private val filteredTransactionAndSignatures = mock<UtxoFilteredTransactionAndSignatures>()
    private val filteredTransactionAndSignatures2 = mock<UtxoFilteredTransactionAndSignatures>()

    private val notarySignature = getSignatureWithMetadataExample()

    private val mockNotarySignatureVerificationService = mock<NotarySignatureVerificationService>()
    private val mockGroupParametersLookup = mock<GroupParametersLookup>()
    private val mockPersistenceService = mock<UtxoLedgerPersistenceService>()

    private val storedFilteredTransactions = mutableListOf<UtxoFilteredTransactionAndSignatures>()

    @BeforeEach
    fun beforeEach() {
        whenever(mockFlowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        originalTransactionalBuilder = utxoLedgerService.createTransactionBuilder()

        storedFilteredTransactions.clear()
        whenever(mockGroupParametersLookup.currentGroupParameters).thenReturn(groupParameters)
        whenever(mockPersistenceService.persistFilteredTransactionsAndSignatures(any())).thenAnswer {
            @Suppress("unchecked_cast")
            storedFilteredTransactions.addAll(it.arguments.first() as List<UtxoFilteredTransactionAndSignatures>)
        }
    }

    @Test
    fun `called with empty builder and receiving empty returns an empty builder`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer())
        )
        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoLedgerService.createTransactionBuilder(), returnedTransactionBuilder)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary null and receives new notary returns a builder with the new notary`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(notaryX500Name, returnedTransactionBuilder.notaryName)
        assertEquals(publicKeyExample, returnedTransactionBuilder.notaryKey)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original notary and receives a different new notary returns with the original notary`() {
        originalTransactionalBuilder.setNotary(notaryX500Name)
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(notaryName = anotherNotaryX500Name))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertEquals(notaryX500Name, returnedTransactionBuilder.notaryName)
        assertEquals(publicKeyExample, returnedTransactionBuilder.notaryKey)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with original time window null and receives new time window returns a builder with the new time window`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample))
        )
        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `Called with original time window and receives a different new time window returns with the original time window`() {
        originalTransactionalBuilder.setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(timeWindow = TimeWindowUntilImpl(Instant.MAX)))
        )

        val returnedTransactionBuilder = callSendFlow()
        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving commands appends them (new, old, duplicated)`() {
        originalTransactionalBuilder.addCommand(command1)
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(commands = mutableListOf(command1, command1, command2)))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(command1, command1, command1, command2), returnedTransactionBuilder.commands)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new signatories appends them`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(signatories = mutableListOf(publicKeyExample, anotherPublicKeyExample))
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing signatories does not append it`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(signatories = mutableListOf(publicKeyExample)))
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving duplicated signatories appends once`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    signatories = mutableListOf(
                        publicKeyExample,
                        anotherPublicKeyExample,
                        anotherPublicKeyExample
                    )
                )
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new input StateRefs appends them`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1, stateRef2)))
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
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1)))
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
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(inputStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2)))
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
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1, stateRef2)))
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
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1)))
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
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(referenceStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2)))
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
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(outputStates = mutableListOf(stateWithEnc1, stateWithEnc1, stateWithEnc2))
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(
            listOf(stateWithEnc1, stateWithEnc1, stateWithEnc1, stateWithEnc2),
            returnedTransactionBuilder.outputStates
        )
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with no dependencies should return the builder without doing any backchain resolution or ftx verification`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(UtxoTransactionBuilderContainer(notaryName = notaryX500Name))
        )

        callSendFlow()

        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
        verify(mockNotarySignatureVerificationService, never()).verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `called with dependencies but no filtered dependencies should result in backchain resolution`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    inputStateRefs = listOf(stateRef1),
                    notaryName = notaryX500Name
                )
            )
        )

        callSendFlow()

        verify(mockFlowEngine).subFlow(any<TransactionBackchainResolutionFlow>())
        verify(mockNotarySignatureVerificationService, never()).verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `called with dependencies and filtered dependencies should result in filtered transaction verification`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    inputStateRefs = listOf(stateRef1),
                    notaryName = notaryX500Name
                ),
                listOf(filteredTransactionAndSignatures)
            )
        )
        whenever(
            mockNotarySignatureVerificationService.verifyNotarySignatures(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer { }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))

        callSendFlow()

        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
        verify(mockNotarySignatureVerificationService).verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )

        Assertions.assertThat(storedFilteredTransactions).containsExactly(filteredTransactionAndSignatures)
    }

    @Test
    fun `called with a filtered dependency that cannot be verified will cause the flow to fail`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    inputStateRefs = listOf(stateRef1),
                    notaryName = notaryX500Name
                ),
                listOf(filteredTransactionAndSignatures)
            )
        )
        whenever(
            mockNotarySignatureVerificationService.verifyNotarySignatures(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer { }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(invalidFilteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        Assertions.assertThat(ex).hasStackTraceContaining("Couldn't verify transaction!")
    }

    @Test
    fun `called with a filtered dependency that has no notary signatures will cause the flow to fail`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    inputStateRefs = listOf(stateRef1),
                    notaryName = notaryX500Name
                ),
                listOf(filteredTransactionAndSignatures)
            )
        )
        whenever(
            mockNotarySignatureVerificationService.verifyNotarySignatures(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer { }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(emptyList())

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        Assertions.assertThat(ex).hasStackTraceContaining("No notary signatures were received")
    }

    @Test
    fun `called with a filtered dependency that has a different notary will cause the flow to fail`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    inputStateRefs = listOf(stateRef1),
                    notaryName = notaryX500Name
                ),
                listOf(filteredTransactionAndSignatures)
            )
        )
        whenever(
            mockNotarySignatureVerificationService.verifyNotarySignatures(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer { }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))
        whenever(filteredTransaction.notaryName).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        Assertions.assertThat(ex).hasStackTraceContaining(
            "Notary name of filtered transaction \"null\" doesn't match with " +
                "notary name of initial transaction \"$notaryX500Name\""
        )
    }

    @Test
    fun `called with a transaction builder that has a notary which is not part of the group params will cause the flow to fail`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    inputStateRefs = listOf(stateRef1),
                    notaryName = anotherNotaryX500Name
                ),
                listOf(filteredTransactionAndSignatures)
            )
        )
        whenever(
            mockNotarySignatureVerificationService.verifyNotarySignatures(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer { }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))
        whenever(filteredTransaction.notaryName).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        Assertions.assertThat(ex).hasStackTraceContaining(
            "Notary from initial transaction \"$anotherNotaryX500Name\" " +
                "cannot be found in group parameter notaries."
        )
    }

    @Test
    fun `called with different number of filtered dependencies and dependencies will cause the flow to fail`() {
        whenever(session.receive(TransactionBuilderPayload::class.java)).thenReturn(
            TransactionBuilderPayload(
                UtxoTransactionBuilderContainer(
                    inputStateRefs = listOf(stateRef1),
                    notaryName = notaryX500Name
                ),
                listOf(filteredTransactionAndSignatures, filteredTransactionAndSignatures2)
            )
        )
        whenever(
            mockNotarySignatureVerificationService.verifyNotarySignatures(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer { }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))
        whenever(filteredTransaction.notaryName).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        Assertions.assertThat(ex).hasStackTraceContaining(
            "The number of filtered transactions received didn't match the number of dependencies."
        )
    }

    private fun callSendFlow(): UtxoTransactionBuilderInternal {
        val flow = ReceiveAndUpdateTransactionBuilderFlowV1(
            session,
            originalTransactionalBuilder as UtxoTransactionBuilderInternal
        )

        flow.flowEngine = mockFlowEngine
        flow.groupParametersLookup = mockGroupParametersLookup
        flow.notaryLookup = mockNotaryLookup
        flow.persistenceService = mockPersistenceService
        flow.notarySignatureVerificationService = mockNotarySignatureVerificationService

        return flow.call() as UtxoTransactionBuilderInternal
    }
}
