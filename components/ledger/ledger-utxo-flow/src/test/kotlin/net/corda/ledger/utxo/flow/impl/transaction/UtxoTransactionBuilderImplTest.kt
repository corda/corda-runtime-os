package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

@Suppress("DEPRECATION")
internal class UtxoTransactionBuilderImplTest: UtxoLedgerTest() {
    @Test
    fun `can build a simple Transaction`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(getUtxoInvalidStateAndRef())
            .addReferenceInputState(getUtxoInvalidStateAndRef())
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .toSignedTransaction(publicKeyExample)
        assertIs<SecureHash>(tx.id)
        assertEquals(getUtxoInvalidStateAndRef().ref, tx.inputStateRefs.single())
        assertEquals(getUtxoInvalidStateAndRef().ref, tx.referenceStateRefs.single())
        assertEquals(utxoStateExample, tx.outputStateAndRefs.single().state.contractState)
        assertEquals(utxoNotaryExample, tx.notary)
        assertEquals(utxoTimeWindowExample, tx.timeWindow)
        assertEquals(publicKeyExample, tx.signatories.first())
    }

    @Test
    fun `can build a simple Transaction with empty component groups`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction(publicKeyExample)
        assertIs<SecureHash>(tx.id)
        assertThat(tx.inputStateRefs).isEmpty()
        assertThat(tx.referenceStateRefs).isEmpty()
        assertEquals(utxoStateExample, tx.outputStateAndRefs.single().state.contractState)
        assertEquals(utxoNotaryExample, tx.notary)
        assertEquals(utxoTimeWindowExample, tx.timeWindow, )
        assertEquals(publicKeyExample, tx.signatories.first())
    }

    // TODO Add tests for verification failures.

    @Test
    fun `includes CPI and CPK information in metadata`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(getUtxoInvalidStateAndRef())
            .addReferenceInputState(getUtxoInvalidStateAndRef())
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .toSignedTransaction(publicKeyExample) as UtxoSignedTransactionImpl

        val metadata = tx.wireTransaction.metadata
        assertEquals(1, metadata.getLedgerVersion())

        val expectedCpiMetadata = CordaPackageSummaryImpl(
            "CPI name",
            "CPI version",
            "46616B652D76616C7565",
            "416E6F746865722D46616B652D76616C7565",
        )
        assertEquals(expectedCpiMetadata, metadata.getCpiMetadata())

        val expectedCpkMetadata = listOf(
            CordaPackageSummaryImpl(
                "MockCpk",
                "1",
                "",
                "0101010101010101010101010101010101010101010101010101010101010101"
            ),
            CordaPackageSummaryImpl(
                "MockCpk",
                "3",
                "",
                "0303030303030303030303030303030303030303030303030303030303030303"
            )
        )
        assertEquals(expectedCpkMetadata, metadata.getCpkMetadata())
    }

    @Test
    fun `can't sign twice`() {
        assertThrows(IllegalStateException::class.java) {
            val builder = utxoTransactionBuilder
                .setNotary(utxoNotaryExample)
                .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
                .addOutputState(utxoStateExample)
                .addInputState(getUtxoInvalidStateAndRef())
                .addReferenceInputState(getUtxoInvalidStateAndRef())
                .addSignatories(listOf(publicKeyExample))
                .addCommand(UtxoCommandExample())
                .addAttachment(SecureHash("SHA-256", ByteArray(12)))

            builder.toSignedTransaction(publicKeyExample)
            builder.toSignedTransaction(publicKeyExample)
        }
    }
}
