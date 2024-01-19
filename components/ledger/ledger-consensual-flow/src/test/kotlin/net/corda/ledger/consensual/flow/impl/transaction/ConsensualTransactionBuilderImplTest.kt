package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.test.dummyCpkSignerSummaryHash
import net.corda.ledger.consensual.test.ConsensualLedgerTest
import net.corda.ledger.consensual.testkit.ConsensualStateClassExample
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

internal class ConsensualTransactionBuilderImplTest : ConsensualLedgerTest() {
    @Test
    fun `can build a simple Transaction`() {
        val tx = consensualTransactionBuilder
            .withStates(consensualStateExample)
            .toSignedTransaction()
        assertIs<SecureHash>(tx.id)
    }

    @Test
    fun `can't sign twice`() {
        assertThrows(IllegalStateException::class.java) {
            val builder = consensualTransactionBuilder
                .withStates(consensualStateExample)

            builder.toSignedTransaction()
            builder.toSignedTransaction()
        }
    }

    @Test
    fun `cannot build Transaction without Consensual States`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            consensualTransactionBuilder.toSignedTransaction()
        }
        assertEquals("At least one consensual state is required.", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            consensualTransactionBuilder
                .withStates(consensualStateExample)
                .withStates(ConsensualStateClassExample("test", emptyList()))
                .toSignedTransaction()
        }
        assertEquals("All consensual states must have participants.", exception.message)
    }

    @Test
    fun `includes CPI and CPK information in metadata`() {
        val tx = consensualTransactionBuilder
            .withStates(consensualStateExample)
            .toSignedTransaction() as ConsensualSignedTransactionImpl

        val metadata = tx.wireTransaction.metadata as TransactionMetadataInternal
        assertEquals(1, metadata.ledgerVersion)

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
                dummyCpkSignerSummaryHash.toString(),
                "SHA-256:0101010101010101010101010101010101010101010101010101010101010101"
            ),
            CordaPackageSummaryImpl(
                "MockCpk",
                "3",
                dummyCpkSignerSummaryHash.toString(),
                "SHA-256:0303030303030303030303030303030303030303030303030303030303030303"
            )
        )
        assertEquals(expectedCpkMetadata, metadata.getCpkMetadata())
    }

    @Test
    fun `adding states mutates and returns the current builder`() {
        val originalTransactionBuilder = consensualTransactionBuilder
        val mutatedTransactionBuilder = consensualTransactionBuilder.withStates(consensualStateExample)
        assertThat(mutatedTransactionBuilder.states).isEqualTo(listOf(consensualStateExample))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }
}
