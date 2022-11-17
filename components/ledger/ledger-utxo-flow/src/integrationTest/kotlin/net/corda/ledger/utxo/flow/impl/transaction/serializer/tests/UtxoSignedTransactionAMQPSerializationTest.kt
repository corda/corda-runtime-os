package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class UtxoSignedTransactionAMQPSerializationTest: UtxoLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a utxo Signed Transaction`() {
        val serialised = serializationOutput.serialize(utxoSignedTransaction, serializationContext)

        // Perform deserialization and check if the correct class is deserialized
        val deserialized =
            deserializationInput.deserialize(serialised, serializationContext)

        assertThat(deserialized.javaClass.name)
            .isEqualTo("net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl")

        assertThat(deserialized)
            .isInstanceOf(UtxoSignedTransaction::class.java)
            .isEqualTo(utxoSignedTransaction)

        assertDoesNotThrow {
            deserialized.id
        }
        assertThat(deserialized.id).isEqualTo(utxoSignedTransaction.id)
    }
}