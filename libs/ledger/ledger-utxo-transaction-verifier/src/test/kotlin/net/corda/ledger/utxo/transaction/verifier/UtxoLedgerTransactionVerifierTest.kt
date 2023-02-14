package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
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

    private val verifier = UtxoLedgerTransactionVerifier( { transaction } )

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(SecureHash("SHA", byteArrayOf(1, 1, 1, 1)))
        whenever(transaction.signatories).thenReturn(listOf(signatory))
        whenever(transaction.inputStateRefs).thenReturn(listOf(stateRef))
        whenever(transaction.outputContractStates).thenReturn(listOf(state))
        whenever(transaction.inputTransactionStates).thenReturn(listOf(inputTransactionState))
        whenever(transaction.referenceTransactionStates).thenReturn(listOf(referenceTransactionState))
        whenever(transaction.commands).thenReturn(listOf(command))
        whenever(transaction.notary).thenReturn(utxoNotaryExample)
        whenever(transaction.metadata).thenReturn(metadata)

        whenever(inputTransactionState.notary).thenReturn(utxoNotaryExample)
        whenever(referenceTransactionState.notary).thenReturn(utxoNotaryExample)
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
    fun `throws an exception if the notary is not allowed`() {
        // TODO CORE-8956 Check the notary is in the group parameters whitelist
    }

    @Test
    fun `throws an exception when input and reference states don't have the same notary`() {
        val anotherNotary = utxoNotaryExample.copy(owningKey = mock())
        whenever(inputTransactionState.notary).thenReturn(anotherNotary)
        whenever(referenceTransactionState.notary).thenReturn(utxoNotaryExample)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Input and reference states' notaries need to be the same.")
    }

    @Test
    fun `throws an exception when input and reference states don't have the same notary passed into the verification`() {
        val anotherNotary = utxoNotaryExample.copy(owningKey = mock())
        whenever(inputTransactionState.notary).thenReturn(anotherNotary)
        whenever(referenceTransactionState.notary).thenReturn(anotherNotary)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Input and reference states' notaries need to be the same as the UtxoLedgerTransaction's notary")
    }

    @Test
    fun `throws an exception if input states are older than output states`() {
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }

    @Test
    fun `catches exceptions from contract verification and outputs them as failure reasons`() {
        val validContractAState = stateAndRef<MyInvalidContractA>(
            SecureHash("SHA", byteArrayOf(1, 1, 1, 1)), 0
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
                override val contractState: UtxoLedgerTransactionContractVerifierTest.MyState = state
                override val contractStateType: Class<out UtxoLedgerTransactionContractVerifierTest.MyState> = state::class.java
                override val contractType: Class<out Contract> = C::class.java
                override val notary: Party = utxoNotaryExample
                override val encumbrance: EncumbranceGroup? = null
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