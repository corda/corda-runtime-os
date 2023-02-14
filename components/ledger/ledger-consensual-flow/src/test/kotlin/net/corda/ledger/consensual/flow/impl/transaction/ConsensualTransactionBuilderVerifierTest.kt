package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualTransactionBuilderVerifier
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class ConsensualTransactionBuilderVerifierTest {

    private val transactionBuilder = mock<ConsensualTransactionBuilder>()
    private val state1 = mock<ConsensualState>()
    private val state2 = mock<ConsensualState>()
    private val signatory = mock<PublicKey>()

    private val verifier = ConsensualTransactionBuilderVerifier(transactionBuilder)

    @BeforeEach
    fun beforeEach() {
        whenever(transactionBuilder.states).thenReturn(listOf(state1, state2))
        whenever(state1.participants).thenReturn(listOf(signatory))
        whenever(state2.participants).thenReturn(listOf(signatory))
    }

    @Test
    fun `a valid transaction does not throw an exception`() {
        verifier.verify()
    }

    @Test
    fun `throws an exception when there are no states`() {
        whenever(transactionBuilder.states).thenReturn(emptyList())
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
}