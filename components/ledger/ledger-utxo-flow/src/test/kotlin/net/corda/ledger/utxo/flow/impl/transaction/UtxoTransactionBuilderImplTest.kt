package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

internal class UtxoTransactionBuilderImplTest: UtxoLedgerTest() {
    @Test
    fun `can build a simple Transaction`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(getUtxoInvalidStateAndRef())
            .addReferenceInputState(getUtxoInvalidStateAndRef())
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .sign(publicKeyExample)
        assertIs<SecureHash>(tx.id)
    }

    @Test
    fun `can build a simple Transaction with empty component groups`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addCommand(UtxoCommandExample())
            .sign(publicKeyExample)
        assertIs<SecureHash>(tx.id)
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
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .sign(publicKeyExample) as UtxoSignedTransactionImpl

        val metadata = tx.wireTransaction.metadata
        assertEquals(1, metadata.getLedgerVersion())

        val expectedCpiMetadata = CordaPackageSummary(
            "CPI name",
            "CPI version",
            "46616B652D76616C7565",
            "416E6F746865722D46616B652D76616C7565",
        )
        assertEquals(expectedCpiMetadata, metadata.getCpiMetadata())

        val expectedCpkMetadata = listOf(
            CordaPackageSummary(
                "MockCpk",
                "1",
                "",
                "0101010101010101010101010101010101010101010101010101010101010101"
            ),
            CordaPackageSummary(
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
                .addCommand(UtxoCommandExample())
                .addAttachment(SecureHash("SHA-256", ByteArray(12)))

            builder.sign(publicKeyExample)
            builder.sign(publicKeyExample)
        }
    }
}
