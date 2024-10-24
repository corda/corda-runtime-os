package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoSignedLedgerTransactionImpl
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class UtxoSignedLedgerTransactionKryoSerializationTest : UtxoLedgerIntegrationTest() {
    @Test
    fun `correct serialization of a UtxoSignedLedgerTransaction`() {
        val utxoSignedLedgerTransaction = UtxoSignedLedgerTransactionImpl(utxoLedgerTransaction, utxoSignedTransaction)
        val bytes = kryoSerializer.serialize(utxoSignedLedgerTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, UtxoSignedLedgerTransaction::class.java)

        assertThat(deserialized).isEqualTo(utxoSignedLedgerTransaction)
        assertDoesNotThrow { deserialized.id }
        assertThat(deserialized.id).isEqualTo(utxoSignedLedgerTransaction.id)
        assertThat(deserialized.ledgerTransaction).isEqualTo(utxoSignedLedgerTransaction.ledgerTransaction)
        assertThat(deserialized.signedTransaction).isEqualTo(utxoSignedLedgerTransaction.signedTransaction)
    }
}
