package net.corda.ledger.utxo.transaction.verifier

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class UtxoTransactionEncumbranceVerifierTest {

    class TestContractState : ContractState {
        override val participants: List<PublicKey>
            get() = TODO("Not yet implemented")
    }
    
    private companion object {
        val notary = Party(MemberX500Name.parse("O=notary, L=London, C=GB"), publicKeyExample)
        val transactionId1 = SecureHash.parse("SHA256:1234567890")
        val transactionId2 = SecureHash.parse("SHA256:ABCDEF0123")
    }
    
    private val transaction = mock<UtxoLedgerTransaction>()

    @Test
    fun `unencumbered states are fine`() {
        val inputs = listOf(
            StateAndRefImpl(TransactionStateImpl(TestContractState(), notary, null),
                StateRef(transactionId1, 0)),
            StateAndRefImpl(TransactionStateImpl(TestContractState(), notary, null),
                StateRef(transactionId2, 1))
        )
        
        whenever(transaction.inputStateAndRefs).thenReturn(inputs)

        val result = verifyEncumbrance(transaction)
        assertThat(result).hasSize(0)
    }

    @Test
    fun `complete encumbrance group is fine`() {
        val inputs = listOf(
            StateAndRefImpl(TransactionStateImpl(
                TestContractState(), notary,
                null), StateRef(transactionId1, 0)),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 1)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 3)
            )
        )

        whenever(transaction.inputStateAndRefs).thenReturn(inputs)

        val result = verifyEncumbrance(transaction)
        assertThat(result).hasSize(0)
    }

    @Test
    fun `duplicate entries do not count`() {
        val state2 = StateAndRefImpl(
            TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
            StateRef(transactionId2, 1)
        )

        val inputs = listOf(
            StateAndRefImpl(TransactionStateImpl(TestContractState(), notary, null),
                StateRef(transactionId1, 0)),
            state2,
            state2
        )

        whenever(transaction.inputStateAndRefs).thenReturn(inputs)

        val result = verifyEncumbrance(transaction)
        assertThat(result).hasSize(1)
        assertThat(result.first().exceptionMessage)
            .isEqualTo("Encumbrance check failed: State SHA256:ABCDEF0123, 1 is used 2 times as input!")
    }

    @Test
    fun `same encumbrance tag from two different tx is fine`() {
        val inputs = listOf(
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId1, 0)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId1, 5)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 1)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 3)
            )
        )

        whenever(transaction.inputStateAndRefs).thenReturn(inputs)

        val result = verifyEncumbrance(transaction)
        assertThat(result).hasSize(0)
    }

    @Test
    fun `incomplete encumbrance group is caught`() {
        val inputs = listOf(
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId1, 0)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 1)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 3)
            )
        )

        whenever(transaction.inputStateAndRefs).thenReturn(inputs)

        val result = verifyEncumbrance(transaction)
        assertThat(result).hasSize(1)
        assertThat(result.first()).extracting { it.exceptionMessage }.isEqualTo(
            "Encumbrance check failed: State $transactionId1, " +
                    "0 is part " +
                    "of encumbrance group test1, but only " +
                    "1 states out of " +
                    "2 encumbered states are present as inputs."
        )
    }

    @Test
    fun `same encumbrance tag from two differend tx cannot complete each other`() {
        val inputs = listOf(
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId1, 0)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 1)
            ),
        )

        whenever(transaction.inputStateAndRefs).thenReturn(inputs)

        val result = verifyEncumbrance(transaction)
        assertThat(result).hasSize(2)
        assertThat(result.first()).extracting { it.exceptionMessage }.isEqualTo(
            "Encumbrance check failed: State $transactionId1, " +
                    "0 is part " +
                    "of encumbrance group test1, but only " +
                    "1 states out of " +
                    "2 encumbered states are present as inputs."
        )
        assertThat(result.last()).extracting { it.exceptionMessage }.isEqualTo(
            "Encumbrance check failed: State $transactionId2, " +
                    "1 is part " +
                    "of encumbrance group test1, but only " +
                    "1 states out of " +
                    "2 encumbered states are present as inputs."
        )
    }

    @Test
    fun `does not crash on silly inputs`() {
        val inputs = listOf(
            StateAndRefImpl(TransactionStateImpl(TestContractState(), notary, null),
                StateRef(transactionId1, 0)),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 1)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 3)
            ),
            StateAndRefImpl(
                TransactionStateImpl(TestContractState(), notary, EncumbranceGroupImpl(2, "test1")),
                StateRef(transactionId2, 4)
            )
        )

        whenever(transaction.inputStateAndRefs).thenReturn(inputs)

        val result = verifyEncumbrance(transaction)
        assertThat(result).hasSize(3)

        assertThat(result.first()).extracting { it.exceptionMessage }.isEqualTo(
            "Encumbrance check failed: State $transactionId2, " +
                    "1 is part " +
                    "of encumbrance group test1, but only " +
                    "3 states out of " +
                    "2 encumbered states are present as inputs."
        )
        assertThat(result.last()).extracting { it.exceptionMessage }.isEqualTo(
            "Encumbrance check failed: State $transactionId2, " +
                    "4 is part " +
                    "of encumbrance group test1, but only " +
                    "3 states out of " +
                    "2 encumbered states are present as inputs."
        )
    }
}