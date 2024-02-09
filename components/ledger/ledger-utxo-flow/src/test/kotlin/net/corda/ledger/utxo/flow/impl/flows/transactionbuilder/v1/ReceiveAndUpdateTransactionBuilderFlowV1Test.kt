package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionAndSignaturesImpl
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
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.GroupParametersLookup
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
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

    private val hash1 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 2, 2)), 0)
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val stateWithEnc1 = ContractStateAndEncumbranceTag(state1, null)
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val stateWithEnc2 = ContractStateAndEncumbranceTag(state2, null)

    private val mockFlowEngine = mock<FlowEngine>()
    private val notaryLookup = mock<NotaryLookup>()
    private val groupParamsLookup = mock<GroupParametersLookup>()
    private val notarySignatureVerificationService = mock<NotarySignatureVerificationService>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()

    private val notaryKey = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(0x01)
    }
    private val mockGroupParameters = mock<GroupParameters>()
    private val mockNotaryInfo = mock<NotaryInfo>()
    private val utxoFilteredTransaction = mock<UtxoFilteredTransaction>()
    private val utxoFilteredTransaction2 = mock<UtxoFilteredTransaction>()
    private val utxoFilteredTransactionInvalid = mock<UtxoFilteredTransaction>()
    private val utxoFilteredTransactionInvalidNotaryName = mock<UtxoFilteredTransaction>()

    private val notarySignature = DigitalSignatureAndMetadata(
        DigitalSignatureWithKeyId(notaryKey.fullIdHash(), byteArrayOf(1, 2, 6)),
        DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
    )

    private val storedFilteredTransaction = mutableListOf<UtxoFilteredTransactionAndSignatures>()

    @BeforeEach
    fun beforeEach() {
        storedFilteredTransaction.clear()
        whenever(mockFlowEngine.subFlow(any<TransactionBackchainResolutionFlow>())).thenReturn(Unit)
        originalTransactionalBuilder = utxoLedgerService.createTransactionBuilder()

        whenever(mockNotaryInfo.publicKey).thenReturn(notaryKey)
        whenever(mockNotaryInfo.name).thenReturn(notaryX500Name)

        // By default, we want backchain logic
        whenever(mockNotaryInfo.isBackchainRequired).thenReturn(true)

        whenever(utxoFilteredTransaction.verify()).doAnswer { }
        whenever(utxoFilteredTransaction.notaryName).thenReturn(notaryX500Name)

        whenever(utxoFilteredTransaction2.verify()).doAnswer { }
        whenever(utxoFilteredTransaction2.notaryName).thenReturn(notaryX500Name)

        whenever(utxoFilteredTransactionInvalidNotaryName.verify()).doAnswer { }
        whenever(utxoFilteredTransactionInvalidNotaryName.notaryName).thenReturn(null)

        whenever(utxoFilteredTransactionInvalid.verify()).doAnswer {
            throw IllegalArgumentException("Could not verify transaction")
        }
        whenever(utxoFilteredTransactionInvalid.notaryName).thenReturn(notaryX500Name)

        whenever(notaryLookup.lookup(eq(notaryX500Name))).thenReturn(mockNotaryInfo)
        whenever(groupParamsLookup.currentGroupParameters).thenReturn(mockGroupParameters)
        whenever(mockGroupParameters.notaries).thenReturn(listOf(mockNotaryInfo))

        whenever(
            notarySignatureVerificationService.verifyNotarySignatures(
                eq(utxoFilteredTransaction),
                eq(notaryKey),
                eq(listOf(notarySignature)),
                eq(emptyMap())
            )
        ).thenAnswer { }

        whenever(
            notarySignatureVerificationService.verifyNotarySignatures(
                eq(utxoFilteredTransaction2),
                eq(notaryKey),
                eq(listOf(notarySignature)),
                eq(emptyMap())
            )
        ).thenAnswer { }

        @Suppress("unchecked_cast")
        whenever(persistenceService.persistFilteredTransactionsAndSignatures(any())).thenAnswer {
            storedFilteredTransaction.addAll(it.arguments.first() as List<UtxoFilteredTransactionAndSignatures>)
        }

        originalTransactionalBuilder.setNotary(notaryX500Name)
    }

    @Test
    fun `called with no notary initially but then receiving one should not throw exception`() {
        // Receive a builder with notary
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(notaryName = notaryX500Name)
        )

        // No notary initially
        callSendFlow(builderOverride = utxoLedgerService.createTransactionBuilder())

        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `called with no notary initially and then not receiving one should throw exception`() {
        // null notary received
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer()
        )

        // No notary initially
        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow(builderOverride = utxoLedgerService.createTransactionBuilder())
        }

        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
        assertThat(ex).hasStackTraceContaining(
            "Notary name was null originally and the received transaction builder didn't provide one either"
        )
    }

    @Test
    fun `notary cannot change on the received builder`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(notaryName = anotherNotaryX500Name)
        )
        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
        assertThat(ex).hasStackTraceContaining("Notary name changed in the received transaction builder")
    }

    @Test
    fun `called with original time window null and receives new time window returns a builder with the new time window`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample, notaryName = notaryX500Name)
        )
        val returnedTransactionBuilder = callSendFlow()

        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `Called with original time window and receives a different new time window returns with the original time window`() {
        originalTransactionalBuilder.setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                timeWindow = TimeWindowUntilImpl(Instant.MAX),
                notaryName = notaryX500Name
            )
        )

        val returnedTransactionBuilder = callSendFlow()
        assertEquals(utxoTimeWindowExample, returnedTransactionBuilder.timeWindow)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving commands appends them (new, old, duplicated)`() {
        originalTransactionalBuilder.addCommand(command1)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                commands = mutableListOf(command1, command1, command2),
                notaryName = notaryX500Name
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(command1, command1, command1, command2), returnedTransactionBuilder.commands)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new signatories appends them`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                signatories = mutableListOf(publicKeyExample, anotherPublicKeyExample),
                notaryName = notaryX500Name
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving existing signatories does not append it`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                signatories = mutableListOf(publicKeyExample),
                notaryName = notaryX500Name
            )
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
                    anotherPublicKeyExample,
                    anotherPublicKeyExample
                ),
                notaryName = notaryX500Name
            )
        )

        val returnedTransactionBuilder = callSendFlow()

        assertContentEquals(listOf(publicKeyExample, anotherPublicKeyExample), returnedTransactionBuilder.signatories)
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `receiving new input StateRefs appends them`() {
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                inputStateRefs = mutableListOf(stateRef1, stateRef2),
                notaryName = notaryX500Name
            )
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
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                inputStateRefs = mutableListOf(stateRef1),
                notaryName = notaryX500Name
            )
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
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                inputStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2),
                notaryName = notaryX500Name
            )
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
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                referenceStateRefs = mutableListOf(stateRef1, stateRef2),
                notaryName = notaryX500Name
            )
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
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                referenceStateRefs = mutableListOf(stateRef1),
                notaryName = notaryX500Name
            )
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
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                referenceStateRefs = mutableListOf(stateRef1, stateRef2, stateRef2),
                notaryName = notaryX500Name
            )
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
        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            UtxoTransactionBuilderContainer(
                outputStates = mutableListOf(stateWithEnc1, stateWithEnc1, stateWithEnc2),
                notaryName = notaryX500Name
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
    fun `notary backchain off - receive filtered transactions and verify them`() {
        val transactionBuilderWithOneInputOneRef = UtxoTransactionBuilderContainer().copy(
            referenceStateRefs = listOf(stateRef1),
            inputStateRefs = listOf(stateRef2),
            filteredDependencies = listOf(
                UtxoFilteredTransactionAndSignaturesImpl(utxoFilteredTransaction, listOf(notarySignature)),
                UtxoFilteredTransactionAndSignaturesImpl(utxoFilteredTransaction2, listOf(notarySignature))
            ),
            notaryName = notaryX500Name
        )

        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            transactionBuilderWithOneInputOneRef
        )

        whenever(mockNotaryInfo.isBackchainRequired).thenReturn(false)

        // This shouldn't encounter any issues
        callSendFlow()

        // Backchain resolution not called
        verify(mockFlowEngine, never()).subFlow(any<TransactionBackchainResolutionFlow>())
    }

    @Test
    fun `notary backchain off - received filtered transaction fails to verify`() {
        val transactionBuilderWithOneInput = UtxoTransactionBuilderContainer().copy(
            inputStateRefs = listOf(stateRef1),
            filteredDependencies = listOf(
                UtxoFilteredTransactionAndSignaturesImpl(utxoFilteredTransactionInvalid, listOf(notarySignature)),
            ),
            notaryName = notaryX500Name
        )

        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            transactionBuilderWithOneInput
        )

        whenever(mockNotaryInfo.isBackchainRequired).thenReturn(false)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("Could not verify transaction")
    }

    @Test
    fun `notary backchain off - received filtered transaction notary mismatch`() {
        val transactionBuilderWithOneInput = UtxoTransactionBuilderContainer().copy(
            inputStateRefs = listOf(stateRef1),
            filteredDependencies = listOf(
                UtxoFilteredTransactionAndSignaturesImpl(utxoFilteredTransactionInvalidNotaryName, listOf(notarySignature)),
            ),
            notaryName = notaryX500Name
        )

        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            transactionBuilderWithOneInput
        )

        whenever(mockNotaryInfo.isBackchainRequired).thenReturn(false)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining(
            "Notary name of filtered transaction \"null\" doesn't match with " +
                "notary name of initial transaction \"$notaryX500Name"
        )
    }

    @Test
    fun `notary backchain off - received filtered transaction fails notary signature verification`() {
        val transactionBuilderWithOneInput = UtxoTransactionBuilderContainer().copy(
            inputStateRefs = listOf(stateRef1),
            filteredDependencies = listOf(
                UtxoFilteredTransactionAndSignaturesImpl(utxoFilteredTransaction, listOf(notarySignature)),
            ),
            notaryName = notaryX500Name
        )

        whenever(session.receive(UtxoTransactionBuilderContainer::class.java)).thenReturn(
            transactionBuilderWithOneInput
        )

        whenever(mockNotaryInfo.isBackchainRequired).thenReturn(false)

        whenever(
            notarySignatureVerificationService.verifyNotarySignatures(
                eq(utxoFilteredTransaction),
                eq(notaryKey),
                eq(listOf(notarySignature)),
                eq(emptyMap())
            )
        ).thenAnswer {
            throw IllegalArgumentException("Notary signature verification failed")
        }

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining(
            "Notary signature verification failed"
        )
    }

    private fun callSendFlow(builderOverride: UtxoTransactionBuilderInternal? = null): UtxoTransactionBuilderInternal {
        val flow = ReceiveAndUpdateTransactionBuilderFlowV1(
            session,
            builderOverride ?: originalTransactionalBuilder as UtxoTransactionBuilderInternal
        ).also {
            it.notaryLookup = notaryLookup
            it.groupParametersLookup = groupParamsLookup
            it.notarySignatureVerificationService = notarySignatureVerificationService
            it.persistenceService = persistenceService
        }

        flow.flowEngine = mockFlowEngine
        return flow.call() as UtxoTransactionBuilderInternal
    }
}
