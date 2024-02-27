package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class UtxoSignedTransactionKryoSerializationTest : UtxoLedgerIntegrationTest() {

    @Test
    fun `correct serialization of a UtxoSignedTransaction`() {
        val bytes = kryoSerializer.serialize(utxoSignedTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, UtxoSignedTransactionInternal::class.java)

        assertThat(deserialized).isEqualTo(utxoSignedTransaction)
        Assertions.assertDoesNotThrow { deserialized.id }
        Assertions.assertEquals(deserialized.id, utxoSignedTransaction.id)
        assertThat(deserialized.wireTransaction).isEqualTo(utxoSignedTransaction.wireTransaction)
    }
}
