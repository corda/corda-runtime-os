package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v2

import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.ReceiveAndUpdateTransactionBuilderFlowCommonTest
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

@Suppress("MaxLineLength")
class ReceiveAndUpdateTransactionBuilderFlowV2Test : ReceiveAndUpdateTransactionBuilderFlowCommonTest() {

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
    fun setup() {
        storedFilteredTransactions.clear()
        originalTransactionalBuilder = utxoLedgerService.createTransactionBuilder()
        whenever(mockGroupParametersLookup.currentGroupParameters).thenReturn(groupParameters)
        whenever(mockPersistenceService.persistFilteredTransactionsAndSignatures(any())).thenAnswer {
            @Suppress("unchecked_cast")
            storedFilteredTransactions.addAll(it.arguments.first() as List<UtxoFilteredTransactionAndSignatures>)
        }
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
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )).doAnswer {  }

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

        assertThat(storedFilteredTransactions).containsExactly(filteredTransactionAndSignatures)
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
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )).doAnswer {  }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(invalidFilteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("Couldn't verify transaction!")
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
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )).doAnswer {  }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(emptyList())

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining("No notary signatures were received")
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
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )).doAnswer {  }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))
        whenever(filteredTransaction.notaryName).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining(
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
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )).doAnswer {  }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))
        whenever(filteredTransaction.notaryName).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining(
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
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(
            any(),
            any(),
            any(),
            any()
        )).doAnswer {  }

        whenever(filteredTransactionAndSignatures.filteredTransaction).thenReturn(filteredTransaction)
        whenever(filteredTransactionAndSignatures.signatures).thenReturn(listOf(notarySignature))
        whenever(filteredTransaction.notaryName).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> {
            callSendFlow()
        }

        assertThat(ex).hasStackTraceContaining(
            "The number of filtered transactions received didn't match the number of dependencies."
        )
    }

    override fun callSendFlow(): UtxoTransactionBuilderInternal {
        val flow = ReceiveAndUpdateTransactionBuilderFlowV2(
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

    override val payloadWrapper: Class<*> = TransactionBuilderPayload::class.java
}
