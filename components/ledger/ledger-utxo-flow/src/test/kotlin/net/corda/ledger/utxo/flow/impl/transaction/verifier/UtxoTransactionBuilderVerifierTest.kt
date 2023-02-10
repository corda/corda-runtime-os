package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.ledger.utxo.flow.impl.transaction.ContractStateAndEncumbranceTag
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class UtxoTransactionBuilderVerifierTest {

    private val transactionBuilder = mock<UtxoTransactionBuilderInternal>()
    private val timeWindow = mock<TimeWindow>()
    private val signatory = mock<PublicKey>()
    private val command = mock<Command>()
    private val state = mock<ContractState>()
    private val stateRef = mock<StateRef>()

    private val verifier = UtxoTransactionBuilderVerifier(transactionBuilder)

    @BeforeEach
    fun beforeEach() {
        whenever(transactionBuilder.notary).thenReturn(utxoNotaryExample)
        whenever(transactionBuilder.timeWindow).thenReturn(timeWindow)
        whenever(transactionBuilder.getEncumbranceGroups()).thenReturn(emptyMap())
        whenever(transactionBuilder.signatories).thenReturn(listOf(signatory))
        whenever(transactionBuilder.inputStateRefs).thenReturn(listOf(stateRef))
        whenever(transactionBuilder.outputStates).thenReturn(listOf(ContractStateAndEncumbranceTag(state, "")))
        whenever(transactionBuilder.commands).thenReturn(listOf(command))
    }

    @Test
    fun `a valid transaction builder does not throw an exception`() {
        verifier.verify()
    }

    @Test
    fun `throws an exception when the notary is null`() {
        whenever(transactionBuilder.notary).thenReturn(null)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("The notary")
    }

    @Test
    fun `throws an exception when the time window is null`() {
        whenever(transactionBuilder.timeWindow).thenReturn(null)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("The time window")
    }

    @Test
    fun `throws an exception when an encumbrance group has no output states in it`() {
        whenever(transactionBuilder.getEncumbranceGroups()).thenReturn(mapOf("" to emptyList()))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Every encumbrance group")
    }

    @Test
    fun `throws an exception when an encumbrance group has one output state in it`() {
        whenever(transactionBuilder.getEncumbranceGroups()).thenReturn(mapOf("" to listOf(state)))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Every encumbrance group")
    }

    @Test
    fun `does not throw an exception when an encumbrance group has more than one output state in it`() {
        whenever(transactionBuilder.getEncumbranceGroups()).thenReturn(mapOf("" to listOf(state, state)))
        verifier.verify()
    }

    @Test
    fun `does not throw an exception when there are no encumbrance groups`() {
        whenever(transactionBuilder.getEncumbranceGroups()).thenReturn(emptyMap())
        verifier.verify()
    }

    @Test
    fun `throws an exception if there are no signatories`() {
        whenever(transactionBuilder.signatories).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one signatory")
    }

    @Test
    fun `throws an exception when there are no input and output states`() {
        whenever(transactionBuilder.inputStateRefs).thenReturn(emptyList())
        whenever(transactionBuilder.outputStates).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one input state, or one output state")
    }

    @Test
    fun `does not throw an exception when there are input states but no output states`() {
        whenever(transactionBuilder.inputStateRefs).thenReturn(listOf(stateRef))
        whenever(transactionBuilder.outputStates).thenReturn(emptyList())
        verifier.verify()
    }

    @Test
    fun `does not throw an exception when there are output states but no input states`() {
        whenever(transactionBuilder.inputStateRefs).thenReturn(emptyList())
        whenever(transactionBuilder.outputStates).thenReturn(listOf(ContractStateAndEncumbranceTag(state, "")))
        verifier.verify()
    }

    @Test
    fun `throws an exception if there are no commands`() {
        whenever(transactionBuilder.commands).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one command")
    }

    @Test
    fun `throws an exception if the notary is not allowed`() {
        // TODO CORE-8956 Check the notary is in the group parameters whitelist
    }
}