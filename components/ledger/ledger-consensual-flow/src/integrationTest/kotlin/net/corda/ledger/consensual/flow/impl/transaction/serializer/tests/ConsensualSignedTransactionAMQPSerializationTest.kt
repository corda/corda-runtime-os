package net.corda.ledger.consensual.flow.impl.transaction.serializer.tests

import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.ledger.consensual.testkit.ConsensualLedgerIntegrationTest
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class ConsensualSignedTransactionAMQPSerializationTest: ConsensualLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a Consensual Signed Transaction`() {

        // Initialised two serialisation factories to avoid having successful tests due to caching
        val factory1 = testDefaultFactory(sandboxGroup)
        val factory2 = testDefaultFactory(sandboxGroup)

        // Initialise the serialisation context
        val testSerializationContext = testSerializationContext.withSandboxGroup(sandboxGroup)

        val serialised = SerializationOutput(factory1).serialize(consensualSignedTransaction, testSerializationContext)

        // Perform deserialization and check if the correct class is deserialized
        val deserialized =
            DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

        assertThat(deserialized.obj.javaClass.name)
            .isEqualTo("net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl")

        assertThat(deserialized.obj)
            .isInstanceOf(ConsensualSignedTransaction::class.java)
            .isEqualTo(consensualSignedTransaction)

        assertDoesNotThrow {
            deserialized.obj.id
        }
        assertThat(deserialized.obj.id).isEqualTo(consensualSignedTransaction.id)
    }
}