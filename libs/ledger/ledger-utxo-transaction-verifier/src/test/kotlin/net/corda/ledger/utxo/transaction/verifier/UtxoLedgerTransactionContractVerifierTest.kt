package net.corda.ledger.utxo.transaction.verifier

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

class UtxoLedgerTransactionContractVerifierTest {

    private companion object {
        val TX_ID_1 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
        val TX_ID_2 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
        val TX_ID_3 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
        val holdingIdentity = HoldingIdentity(MemberX500Name("ALICE", "LDN", "GB"), "group")
    }

    private val transaction = mock<UtxoLedgerTransaction>()
    private val transactionFactory = mock<() -> UtxoLedgerTransaction>()
    private val injectService = mock<(Contract) -> Unit>()

    @BeforeEach
    fun beforeEach() {
        MyValidContractA.EXECUTION_COUNT = 0
        MyValidContractB.EXECUTION_COUNT = 0
        MyValidContractC.EXECUTION_COUNT = 0
        whenever(transactionFactory.invoke()).thenReturn(transaction)
        whenever(transaction.id).thenReturn(TX_ID_1)
    }

    @Test
    fun `executes contract verification on each input and output state grouped by contract type`() {
        val validContractAState = stateAndRef<MyValidContractA>(TX_ID_2, 0)
        val validContractBState = stateAndRef<MyValidContractB>(TX_ID_3, 1)
        val validContractCState1 = stateAndRef<MyValidContractC>(TX_ID_2, 1)
        val validContractCState2 = stateAndRef<MyValidContractC>(TX_ID_1, 0)
        whenever(transaction.inputStateAndRefs).thenReturn(
            listOf(
                validContractAState,
                validContractBState,
                validContractCState1
            )
        )
        whenever(transaction.outputStateAndRefs).thenReturn(listOf(validContractCState2))
        verifyContracts(transactionFactory, transaction, holdingIdentity, injectService)
        // Called once for each of 3 contracts
        verify(transactionFactory, times(3)).invoke()
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
        whenever(transaction.inputStateAndRefs).thenReturn(
            listOf(
                validContractAState,
                validContractBState,
                invalidContractAState
            )
        )
        whenever(transaction.outputStateAndRefs).thenReturn(listOf(invalidContractBState))
        assertThatThrownBy { verifyContracts(transactionFactory, transaction, holdingIdentity, injectService) }
            .isExactlyInstanceOf(ContractVerificationException::class.java)
            .hasMessageContainingAll("I have failed", "Something is wrong here")
        // Called once for each of 4 contracts
        verify(transactionFactory, times(4)).invoke()
        assertThat(MyValidContractA.EXECUTION_COUNT).isEqualTo(1)
        assertThat(MyValidContractB.EXECUTION_COUNT).isEqualTo(1)
    }

    private inline fun <reified C : Contract> stateAndRef(transactionId: SecureHash, index: Int): StateAndRef<MyState> {
        val state = MyState()
        return StateAndRefImpl(
            object : TransactionState<MyState> {
                override fun getContractState(): MyState {
                    return state
                }

                override fun getContractStateType(): Class<MyState> {
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

    class MyState : ContractState {
        override fun getParticipants(): List<PublicKey> {
            return listOf()
        }
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
