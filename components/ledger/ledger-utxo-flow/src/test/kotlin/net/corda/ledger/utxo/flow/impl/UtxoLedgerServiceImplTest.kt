package net.corda.ledger.utxo.flow.impl

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoSignedTransactionWithDependencies
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertIs

class UtxoLedgerServiceImplTest : UtxoLedgerTest() {

    @Test
    fun `createTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = utxoLedgerService.createTransactionBuilder()
        assertIs<UtxoTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `UtxoLedgerServiceImpl createTransactionBuilder() can build a SignedTransaction`() {
        val transactionBuilder = utxoLedgerService.createTransactionBuilder()

        val inputStateAndRef = getExampleStateAndRefImpl(1)
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getExampleStateAndRefImpl(2)
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef, referenceStateAndRef))

        val command = UtxoCommandExample()

        val signedTransaction = transactionBuilder
            .setNotary(notaryX500Name)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(command)
            .toSignedTransaction()

        assertIs<UtxoSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)

        val ledgerTransaction = signedTransaction.toLedgerTransaction()

        assertIs<SecureHash>(ledgerTransaction.id)

        Assertions.assertEquals(utxoTimeWindowExample, ledgerTransaction.timeWindow)

        assertIs<List<ContractState>>(ledgerTransaction.outputContractStates)
        Assertions.assertEquals(1, ledgerTransaction.outputContractStates.size)
        Assertions.assertEquals(getUtxoStateExample(), ledgerTransaction.outputContractStates.first())
        assertIs<UtxoStateClassExample>(ledgerTransaction.outputContractStates.first())

        assertIs<List<PublicKey>>(ledgerTransaction.signatories)
        Assertions.assertEquals(1, ledgerTransaction.signatories.size)
        Assertions.assertEquals(publicKeyExample, ledgerTransaction.signatories.first())
        assertIs<PublicKey>(ledgerTransaction.signatories.first())
    }

    @Test
    fun `findFilteredTransactionsAndSignatures will not go to the database if a UtxoSignedTransactionWithDependencies is passed in`() {
        val filteredTransactionMock = mock<UtxoFilteredTransaction> {
            on { id } doReturn mock()
        }

        val filteredTransactionAndSignatureMock = mock<UtxoFilteredTransactionAndSignatures> {
            on { filteredTransaction } doReturn filteredTransactionMock
        }

        utxoLedgerService.findFilteredTransactionsAndSignatures(
            UtxoSignedTransactionWithDependencies(
                mock(),
                listOf(filteredTransactionAndSignatureMock)
            )
        )

        verify(mockUtxoLedgerPersistenceService, never()).findFilteredTransactionsAndSignatures(any(), any(), any())
    }

    @Test
    fun `findFilteredTransactionsAndSignatures will go to the database if a UtxoSignedTransaction is passed in`() {
        val ref = StateRef(mock(), 0)
        val ref2 = StateRef(mock(), 1)

        val signedTransactionInternal = mock<UtxoSignedTransactionInternal> {
            on { inputStateRefs } doReturn listOf(ref)
            on { referenceStateRefs } doReturn listOf(ref2)
            on { notaryName } doReturn notaryX500Name
            on { notaryKey } doReturn mock()
        }

        utxoLedgerService.findFilteredTransactionsAndSignatures(
            signedTransactionInternal
        )

        verify(mockUtxoLedgerPersistenceService, times(1))
            .findFilteredTransactionsAndSignatures(any(), any(), any())
    }
}
