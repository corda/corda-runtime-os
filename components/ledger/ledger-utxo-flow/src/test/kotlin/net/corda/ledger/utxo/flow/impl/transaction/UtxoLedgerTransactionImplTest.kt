package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertIs

internal class UtxoLedgerTransactionImplTest: UtxoLedgerTest() {
    @Test
    fun `ledger transaction contains the same data what it was created with`() {

        val inputStateAndRef = getUtxoInvalidStateAndRef()
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getUtxoInvalidStateAndRef()
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef))

        val command = UtxoCommandExample()
        val attachment = SecureHash("SHA-256", ByteArray(12))

        val signedTransaction = UtxoTransactionBuilderImpl(
            utxoSignedTransactionFactory
        )
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(command)
            .addAttachment(attachment)
            .toSignedTransaction()
        val ledgerTransaction = signedTransaction.toLedgerTransaction()

        assertIs<SecureHash>(ledgerTransaction.id)

        assertEquals(utxoTimeWindowExample, ledgerTransaction.timeWindow)

        assertIs<List<ContractState>>(ledgerTransaction.outputContractStates)
        assertEquals(1, ledgerTransaction.outputContractStates.size)
        assertEquals(utxoStateExample, ledgerTransaction.outputContractStates.first())
        assertIs<UtxoStateClassExample>(ledgerTransaction.outputContractStates.first())

        assertIs<List<PublicKey>>(ledgerTransaction.signatories)
        assertEquals(1, ledgerTransaction.signatories.size)
        assertEquals(publicKeyExample, ledgerTransaction.signatories.first())
        assertIs<PublicKey>(ledgerTransaction.signatories.first())

        assertIs<List<StateAndRef<UtxoStateClassExample>>>(ledgerTransaction.inputStateAndRefs)
        assertEquals(1, ledgerTransaction.inputStateAndRefs.size)
        assertEquals(inputStateAndRef, ledgerTransaction.inputStateAndRefs.first())
        assertIs<StateAndRef<UtxoStateClassExample>>(ledgerTransaction.inputStateAndRefs.first())

        assertIs<List<StateAndRef<UtxoStateClassExample>>>(ledgerTransaction.referenceStateAndRefs)
        assertEquals(1, ledgerTransaction.referenceStateAndRefs.size)
        assertEquals(referenceStateAndRef, ledgerTransaction.referenceStateAndRefs.first())
        assertIs<StateAndRef<UtxoStateClassExample>>(ledgerTransaction.referenceStateAndRefs.first())

        // TODO Also test Commands and Attachments when they get deserialized properly.
    }
}
