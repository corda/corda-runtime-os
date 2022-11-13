package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class UtxoSignedTransactionAMQPSerializationTest: UtxoLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a utxo Signed Transaction`() {

        // Initialised two serialisation factories to avoid having successful tests due to caching
        val factory1 = testDefaultFactory(sandboxGroup)
        val factory2 = testDefaultFactory(sandboxGroup)

        // Initialise the serialisation context
        val testSerializationContext = testSerializationContext.withSandboxGroup(sandboxGroup)
        
        val serialised = SerializationOutput(factory1).serialize(utxoSignedTransaction, testSerializationContext)


        // Perform deserialization and check if the correct class is deserialized
        val deserialized =
            DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

        assertThat(deserialized.obj.javaClass.name)
            .isEqualTo("net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl")

        assertThat(deserialized.obj)
            .isInstanceOf(UtxoSignedTransaction::class.java)
            .isEqualTo(utxoSignedTransaction)

        assertDoesNotThrow {
            deserialized.obj.id
        }
        assertThat(deserialized.obj.id).isEqualTo(utxoSignedTransaction.id)
    }
}