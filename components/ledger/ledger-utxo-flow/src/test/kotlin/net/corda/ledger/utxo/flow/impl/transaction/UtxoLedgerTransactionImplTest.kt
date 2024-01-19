package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertIs

internal class UtxoLedgerTransactionImplTest : UtxoLedgerTest() {
    private val inputStateAndRef = getExampleStateAndRefImpl()
    private val inputStateRef = inputStateAndRef.ref
    private val referenceStateAndRef = getExampleStateAndRefImpl(2)
    private val referenceStateRef = referenceStateAndRef.ref

    private val command = UtxoCommandExample()

    private lateinit var ledgerTransaction: UtxoLedgerTransaction

    @BeforeEach
    fun beforeEach() {
        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef, referenceStateAndRef))

        val signedTransaction = UtxoTransactionBuilderImpl(
            utxoSignedTransactionFactory,
            mockNotaryLookup
        )
            .setNotary(notaryX500Name)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(command)
            .toSignedTransaction()
        ledgerTransaction = signedTransaction.toLedgerTransaction()
    }

    @Test
    fun `ledger transaction can calculate id`() {
        assertIs<SecureHash>(ledgerTransaction.id)
    }

    @Test
    fun `ledger transaction have the same TimeWindow it was created with`() {
        assertEquals(utxoTimeWindowExample, ledgerTransaction.timeWindow)
    }

    @Test
    fun `ledger transaction have the same Command it was created with`() {
        assertEquals(listOf(command), ledgerTransaction.commands)
    }

    @Test
    fun `ledger transaction have the same outputContractStates it was created with`() {
        assertIs<List<ContractState>>(ledgerTransaction.outputContractStates)
        assertEquals(1, ledgerTransaction.outputContractStates.size)
        assertEquals(getUtxoStateExample(), ledgerTransaction.outputContractStates.first())
        assertIs<UtxoStateClassExample>(ledgerTransaction.outputContractStates.first())
    }

    @Test
    fun `ledger transaction have the same signatories it was created with`() {
        assertIs<List<PublicKey>>(ledgerTransaction.signatories)
        assertEquals(1, ledgerTransaction.signatories.size)
        assertEquals(publicKeyExample, ledgerTransaction.signatories.first())
        assertIs<PublicKey>(ledgerTransaction.signatories.first())
    }

    @Test
    fun `ledger transaction have the same input StateAndRefs it was created with`() {
        assertIs<List<StateAndRef<UtxoStateClassExample>>>(ledgerTransaction.inputStateAndRefs)
        assertEquals(1, ledgerTransaction.inputStateAndRefs.size)
        assertEquals(inputStateAndRef, ledgerTransaction.inputStateAndRefs.first())
        assertIs<StateAndRef<UtxoStateClassExample>>(ledgerTransaction.inputStateAndRefs.first())
    }

    @Test
    fun `ledger transaction have the same reference StateAndRefs it was created with`() {
        assertIs<List<StateAndRef<UtxoStateClassExample>>>(ledgerTransaction.referenceStateAndRefs)
        assertEquals(1, ledgerTransaction.referenceStateAndRefs.size)
        assertEquals(referenceStateAndRef, ledgerTransaction.referenceStateAndRefs.first())
        assertIs<StateAndRef<UtxoStateClassExample>>(ledgerTransaction.referenceStateAndRefs.first())
    }

    @Test
    fun canCastInputStateAndRefsToItself() {
        assertEquals(
            listOf(inputStateAndRef),
            ledgerTransaction.getInputStateAndRefs(UtxoStateClassExample::class.java)
        )
    }

    @Test
    fun canCastReferenceStateAndRefsToItself() {
        assertEquals(
            listOf(referenceStateAndRef),
            ledgerTransaction.getReferenceStateAndRefs(UtxoStateClassExample::class.java)
        )
    }

    @Test
    fun canCastOutputStateAndRefsToItself() {
        assertEquals(
            listOf(getUtxoStateExample()),
            ledgerTransaction.getOutputStateAndRefs(UtxoStateClassExample::class.java).map { it.state.contractState }
        )
    }

    @Test
    fun canCastInputStateAndRefsToContractState() {
        assertEquals(
            listOf(inputStateAndRef),
            ledgerTransaction.getInputStateAndRefs(ContractState::class.java)
        )
    }

    @Test
    fun canCastReferenceStateAndRefsToContractState() {
        assertEquals(
            listOf(referenceStateAndRef),
            ledgerTransaction.getReferenceStateAndRefs(ContractState::class.java)
        )
    }

    @Test
    fun canCastOutputStateAndRefsToContractState() {
        assertEquals(
            listOf(getUtxoStateExample()),
            ledgerTransaction.getOutputStateAndRefs(ContractState::class.java).map { it.state.contractState }
        )
    }

    // TODO Also test Attachments when they get deserialized properly.
}
