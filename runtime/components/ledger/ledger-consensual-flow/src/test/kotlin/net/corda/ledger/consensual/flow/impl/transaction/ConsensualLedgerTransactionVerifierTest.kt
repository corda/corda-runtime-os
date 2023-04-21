package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualLedgerTransactionVerifier
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

class ConsensualLedgerTransactionVerifierTest {

    private val transaction = mock<ConsensualLedgerTransaction>()
    private val state1 = mock<ConsensualState>()
    private val state2 = mock<ConsensualState>()
    private val signatory = mock<PublicKey>()

    private val verifier = ConsensualLedgerTransactionVerifier(transaction)

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.states).thenReturn(listOf(state1, state2))
        whenever(transaction.requiredSignatories).thenReturn(setOf(signatory))
        whenever(state1.participants).thenReturn(listOf(signatory))
        whenever(state2.participants).thenReturn(listOf(signatory))
    }

    @Test
    fun `a valid transaction does not throw an exception`() {
        verifier.verify()
    }

    @Test
    fun `throws an exception when there are no states`() {
        whenever(transaction.states).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one consensual state is required")
    }

    @Test
    fun `throws an exception when any state does not have participants`() {
        whenever(state1.participants).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("All consensual states must have participants")
    }

    @Test
    fun `throws an exception when the signatories for the transaction does not match the participants of the states`() {
        val anotherSignatory = mock<PublicKey>()
        whenever(transaction.requiredSignatories).thenReturn(setOf(anotherSignatory))
        whenever(state1.participants).thenReturn(listOf(signatory))
        whenever(state2.participants).thenReturn(listOf(signatory))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(
                "Deserialized required signatories from ${WireTransaction::class.java.simpleName} do not match with the ones derived " +
                        "from the states"
            )
    }

    @Test
    fun `executes consensual state verification`() {
        verifier.verify()
        verify(state1).verify(transaction)
        verify(state2).verify(transaction)
    }
}