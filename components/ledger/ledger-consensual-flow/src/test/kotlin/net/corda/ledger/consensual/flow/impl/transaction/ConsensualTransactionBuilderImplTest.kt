package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.test.dummyCpkSignerSummaryHash
import net.corda.ledger.consensual.test.ConsensualLedgerTest
import net.corda.ledger.consensual.testkit.ConsensualStateClassExample
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

internal class ConsensualTransactionBuilderImplTest: ConsensualLedgerTest() {
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
        val exception = assertThrows(IllegalArgumentException::class.java) {
            consensualTransactionBuilder.toSignedTransaction()
        }
        assertEquals("At least one consensual state is required", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            consensualTransactionBuilder
                .withStates(consensualStateExample)
                .withStates(ConsensualStateClassExample("test", emptyList()))
                .toSignedTransaction()
        }
        assertEquals("All consensual states must have participants", exception.message)
    }

    @Test
    fun `includes CPI and CPK information in metadata`() {
        val tx = consensualTransactionBuilder
            .withStates(consensualStateExample)
            .toSignedTransaction() as ConsensualSignedTransactionImpl

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
                dummyCpkSignerSummaryHash.toHexString(),
                "0101010101010101010101010101010101010101010101010101010101010101"
            ),
            CordaPackageSummaryImpl(
                "MockCpk",
                "3",
                dummyCpkSignerSummaryHash.toHexString(),
                "0303030303030303030303030303030303030303030303030303030303030303"
            )
        )
        assertEquals(expectedCpkMetadata, metadata.getCpkMetadata())
    }
}
