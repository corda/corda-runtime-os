package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class UtxoLedgerTransactionKryoSerializationTest : UtxoLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `correct serialization of a utxo Signed Transaction`() {
        val bytes = kryoSerializer.serialize(utxoLedgerTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, UtxoLedgerTransactionInternal::class.java)

        assertThat(deserialized).isEqualTo(utxoLedgerTransaction)
        assertDoesNotThrow { deserialized.id }
        assertThat(deserialized.id).isEqualTo(utxoLedgerTransaction.id)
        assertThat(deserialized.wireTransaction).isEqualTo(utxoLedgerTransaction.wireTransaction)
    }
}
