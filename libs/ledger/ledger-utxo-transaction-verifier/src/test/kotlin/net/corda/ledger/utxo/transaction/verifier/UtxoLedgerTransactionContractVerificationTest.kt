package net.corda.ledger.utxo.transaction.verifier

import io.micrometer.core.instrument.Timer
import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.libs.verification.impl.UtxoLedgerTransactionVerifierImpl
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.util.concurrent.Callable

class UtxoLedgerTransactionContractVerificationTest {

    private val transaction = mock<UtxoLedgerTransaction>()
    private val signatory = mock<PublicKey>()
    private val command = mock<Command>()
    private val state = mock<ContractState>()
    private val stateRef = mock<StateRef>()
    private val inputTransactionState = mock<TransactionState<ContractState>>()
    private val referenceTransactionState = mock<TransactionState<ContractState>>()
    private val metadata = mock<TransactionMetadata>()
    private val injectionService = mock<(Contract) -> Unit>()
    private val callable = argumentCaptor<Callable<Any>>()
    private val timer = mock<Timer> {
        on { recordCallable(callable.capture()) } doAnswer { callable.lastValue.call() }
    }
    private val metricFactory = mock<ContractVerificationMetricFactory> {
        on { getContractVerificationTimeMetric() } doReturn timer
        on { getContractVerificationContractCountMetric() } doReturn mock()
        on { getContractVerificationContractTime(any()) } doReturn timer
    }
    private val verifier = UtxoLedgerTransactionVerifierImpl({ transaction }, transaction) {
        verifyContracts({ transaction }, transaction, injectionService, metricFactory)
    }

    @BeforeEach
    fun beforeEach() {
        whenever(metadata.getLedgerModel()).thenReturn(UtxoLedgerTransactionImpl::class.java.name)
        whenever(metadata.getTransactionSubtype()).thenReturn("GENERAL")

        whenever(transaction.id).thenReturn(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)))
        whenever(transaction.signatories).thenReturn(listOf(signatory))
        whenever(transaction.inputStateRefs).thenReturn(listOf(stateRef))
        whenever(transaction.outputContractStates).thenReturn(listOf(state))
        whenever(transaction.inputTransactionStates).thenReturn(listOf(inputTransactionState))
        whenever(transaction.referenceTransactionStates).thenReturn(listOf(referenceTransactionState))
        whenever(transaction.commands).thenReturn(listOf(command))
        whenever(transaction.notaryName).thenReturn(notaryX500Name)
        whenever(transaction.notaryKey).thenReturn(publicKeyExample)
        whenever(transaction.metadata).thenReturn(metadata)

        whenever(inputTransactionState.notaryName).thenReturn(notaryX500Name)
        whenever(inputTransactionState.notaryKey).thenReturn(publicKeyExample)
        whenever(referenceTransactionState.notaryName).thenReturn(notaryX500Name)
        whenever(referenceTransactionState.notaryKey).thenReturn(publicKeyExample)
    }

    @Test
    fun `catches exceptions from contract verification and outputs them as failure reasons`() {
        val validContractAState = stateAndRef<MyInvalidContractA>(
            SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)),
            0
        )
        whenever(transaction.inputStateAndRefs).thenReturn(listOf(validContractAState))
        whenever(transaction.outputStateAndRefs).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(ContractVerificationException::class.java)
            .hasMessageContainingAll("I have failed")
    }

    private inline fun <reified C : Contract> stateAndRef(
        transactionId: SecureHash,
        index: Int
    ): StateAndRef<UtxoLedgerTransactionContractVerifierTest.MyState> {
        val state = UtxoLedgerTransactionContractVerifierTest.MyState()
        return StateAndRefImpl(
            object : TransactionState<UtxoLedgerTransactionContractVerifierTest.MyState> {
                override fun getContractState(): UtxoLedgerTransactionContractVerifierTest.MyState {
                    return state
                }

                override fun getContractStateType(): Class<UtxoLedgerTransactionContractVerifierTest.MyState> {
                    return state.javaClass
                }

                override fun getContractType(): Class<out Contract> {
                    return C::class.java
                }

                override fun getNotaryName(): MemberX500Name {
                    return notaryX500Name
                }

                override fun getNotaryKey(): PublicKey {
                    return publicKeyExample
                }

                override fun getEncumbranceGroup(): EncumbranceGroup? {
                    return null
                }
            },
            StateRef(transactionId, index)
        )
    }

    class MyInvalidContractA : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {
            throw IllegalStateException("I have failed")
        }
    }
}
