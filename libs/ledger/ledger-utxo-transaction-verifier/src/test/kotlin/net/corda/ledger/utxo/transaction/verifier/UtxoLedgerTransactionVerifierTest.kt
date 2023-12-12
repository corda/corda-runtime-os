package net.corda.ledger.utxo.transaction.verifier

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
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
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class UtxoLedgerTransactionVerifierTest {

    private val transaction = mock<UtxoLedgerTransaction>()
    private val signatory = mock<PublicKey>()
    private val command = mock<Command>()
    private val state = mock<ContractState>()
    private val stateRef = mock<StateRef>()
    private val inputTransactionState = mock<TransactionState<ContractState>>()
    private val referenceTransactionState = mock<TransactionState<ContractState>>()
    private val metadata = mock<TransactionMetadata>()
    private val holdingIdentity = HoldingIdentity(MemberX500Name("ALICE", "LDN", "GB"), "group")
    private val injectionService = mock<(Contract) -> Unit>()
    private val verifier = UtxoLedgerTransactionVerifier({ transaction }, transaction, holdingIdentity, injectionService)

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
    fun `a valid transaction does not throw an exception`() {
        verifier.verify()
    }

    @Test
    fun `throws an exception if there are no signatories`() {
        whenever(transaction.signatories).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one signatory")
    }

    @Test
    fun `throws an exception when there are no input and output states`() {
        whenever(transaction.inputStateRefs).thenReturn(emptyList())
        whenever(transaction.outputContractStates).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one input state, or one output state")
    }

    @Test
    fun `does not throw an exception when there are input states but no output states`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(stateRef))
        whenever(transaction.outputContractStates).thenReturn(emptyList())
        verifier.verify()
    }

    @Test
    fun `does not throw an exception when there are output states but no input states`() {
        whenever(transaction.inputStateRefs).thenReturn(emptyList())
        whenever(transaction.outputContractStates).thenReturn(listOf(state))
        verifier.verify()
    }

    @Test
    fun `throws an exception if there are no commands`() {
        whenever(transaction.commands).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one command")
    }

    @Test
    fun `throws an exception if the same input state appears twice`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(stateRef, stateRef))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Duplicate input states detected")
    }

    @Test
    fun `throws an exception if the same reference state appears twice`() {
        val referenceStateRef = mock<StateRef>()

        whenever(transaction.referenceStateRefs).thenReturn(listOf(referenceStateRef, referenceStateRef))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Duplicate reference states detected")
    }

    @Test
    fun `throws an exception if there are overlapping input and reference states`() {
        whenever(transaction.referenceStateRefs).thenReturn(listOf(stateRef))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cannot be both an input and a reference input in the same transaction.")
    }

    @Test
    fun `throws an exception when input and reference states don't have the same notary (names are different)`() {
        whenever(inputTransactionState.notaryName).thenReturn(notaryX500Name)
        whenever(referenceTransactionState.notaryName).thenReturn(anotherNotaryX500Name)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Input and reference states' notaries need to be the same.")
    }

    @Test
    fun `does not throw when input and reference states don't have the same notary keys (but the names are still the same)`() {
        whenever(inputTransactionState.notaryKey).thenReturn(publicKeyExample)
        whenever(referenceTransactionState.notaryKey).thenReturn(anotherPublicKeyExample)
        assertDoesNotThrow { verifier.verify() }
    }

    @Test
    fun `throws an exception when input and reference states don't have the same notary passed into verification (names are different)`() {
        whenever(inputTransactionState.notaryName).thenReturn(anotherNotaryX500Name)
        whenever(referenceTransactionState.notaryName).thenReturn(anotherNotaryX500Name)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Input and reference states' notaries need to be the same as the UtxoLedgerTransaction's notary")
    }

    @Test
    fun `does not throw when input and reference states have different notary keys passed into verification (names are the same)`() {
        whenever(inputTransactionState.notaryKey).thenReturn(anotherPublicKeyExample)
        whenever(referenceTransactionState.notaryKey).thenReturn(anotherPublicKeyExample)
        assertDoesNotThrow { verifier.verify() }
    }

    @Test
    fun `throws an exception if input states are older than output states`() {
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
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
