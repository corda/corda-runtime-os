package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class UtxoLedgerTransactionContractVerifierTest {

    private companion object {
        val TX_ID_1 = SecureHash("SHA", byteArrayOf(1, 1, 1, 1))
        val TX_ID_2 = SecureHash("SHA", byteArrayOf(1, 1, 1, 1))
        val TX_ID_3 = SecureHash("SHA", byteArrayOf(1, 1, 1, 1))
    }

    private val transaction = mock<UtxoLedgerTransaction>()

    @BeforeEach
    fun beforeEach() {
        MyValidContractA.EXECUTION_COUNT = 0
        MyValidContractB.EXECUTION_COUNT = 0
        MyValidContractC.EXECUTION_COUNT = 0
        whenever(transaction.id).thenReturn(TX_ID_1)
    }

    @Test
    fun `executes contract verification on each input and output state grouped by contract type`() {
        val validContractAState = stateAndRef<MyValidContractA>(TX_ID_2, 0)
        val validContractBState = stateAndRef<MyValidContractB>(TX_ID_3, 1)
        val validContractCState1 = stateAndRef<MyValidContractC>(TX_ID_2, 1)
        val validContractCState2 = stateAndRef<MyValidContractC>(TX_ID_1, 0)
        whenever(transaction.inputStateAndRefs).thenReturn(listOf(validContractAState, validContractBState, validContractCState1))
        whenever(transaction.outputStateAndRefs).thenReturn(listOf(validContractCState2))
        UtxoLedgerTransactionContractVerifier(transaction).verify()
        assertThat(MyValidContractA.EXECUTION_COUNT).isEqualTo(1)
        assertThat(MyValidContractB.EXECUTION_COUNT).isEqualTo(1)
        assertThat(MyValidContractC.EXECUTION_COUNT).isEqualTo(1)
    }

    @Test
    fun `catches exceptions from contract verification and outputs them as failure reasons`() {
        val validContractAState = stateAndRef<MyValidContractA>(TX_ID_2, 0)
        val validContractBState = stateAndRef<MyValidContractB>(TX_ID_3, 1)
        val invalidContractAState = stateAndRef<MyInvalidContractA>(TX_ID_2, 1)
        val invalidContractBState = stateAndRef<MyInvalidContractB>(TX_ID_1, 0)
        whenever(transaction.inputStateAndRefs).thenReturn(listOf(validContractAState, validContractBState, invalidContractAState))
        whenever(transaction.outputStateAndRefs).thenReturn(listOf(invalidContractBState))
        assertThatThrownBy { UtxoLedgerTransactionContractVerifier(transaction).verify() }
            .isExactlyInstanceOf(ContractVerificationException::class.java)
            .hasMessageContainingAll("I have failed", "Something is wrong here")
        assertThat(MyValidContractA.EXECUTION_COUNT).isEqualTo(1)
        assertThat(MyValidContractB.EXECUTION_COUNT).isEqualTo(1)
    }

    private inline fun <reified C : Contract> stateAndRef(transactionId: SecureHash, index: Int): StateAndRef<MyState> {
        val state = MyState()
        return StateAndRefImpl(
            object : TransactionState<MyState> {
                override val contractState: MyState = state
                override val contractStateType: Class<out MyState> = state::class.java
                override val contractType: Class<out Contract> = C::class.java
                override val notary: Party = utxoNotaryExample
                override val encumbrance: EncumbranceGroup? = null
            },
            StateRef(transactionId, index)
        )
    }

    class MyState : ContractState {
        override val participants: List<PublicKey> = emptyList()
    }

    class MyValidContractA : Contract {

        companion object {
            var EXECUTION_COUNT = 0
        }

        override fun verify(transaction: UtxoLedgerTransaction) {
            EXECUTION_COUNT += 1
        }
    }

    class MyValidContractB : Contract {
        companion object {
            var EXECUTION_COUNT = 0
        }

        override fun verify(transaction: UtxoLedgerTransaction) {
            EXECUTION_COUNT += 1
        }
    }

    class MyValidContractC : Contract {
        companion object {
            var EXECUTION_COUNT = 0
        }

        override fun verify(transaction: UtxoLedgerTransaction) {
            EXECUTION_COUNT += 1
        }
    }

    class MyInvalidContractA : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {
            throw IllegalStateException("I have failed")
        }
    }

    @Suppress("TooGenericExceptionThrown")
    class MyInvalidContractB : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {
            throw Exception("Something is wrong here")
        }
    }
}