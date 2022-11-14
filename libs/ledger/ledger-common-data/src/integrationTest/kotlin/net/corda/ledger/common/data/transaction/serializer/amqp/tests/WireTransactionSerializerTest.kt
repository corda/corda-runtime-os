package net.corda.ledger.common.data.transaction.serializer.amqp.tests

import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.ledger.common.integrationtest.CommonLedgerIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class WireTransactionSerializerTest: CommonLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a wireTransaction`() {
        // Create sandbox group
        // Initialised two serialisation factories to avoid having successful tests due to caching
        val factory1 = testDefaultFactory(sandboxGroup)
        val factory2 = testDefaultFactory(sandboxGroup)

        // Initialise the serialisation context
        val testSerializationContext = testSerializationContext.withSandboxGroup(sandboxGroup)

        val serialised = SerializationOutput(factory1).serialize(wireTransaction, testSerializationContext)

        // Perform deserialization and check if the correct class is deserialized
        val deserialized =
            DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

        assertThat(deserialized.obj.javaClass.name).isEqualTo(
            "net.corda.ledger.common.data.transaction.WireTransaction"
        )

        assertThat(deserialized.obj).isEqualTo(wireTransaction)
        Assertions.assertDoesNotThrow {
            deserialized.obj.id
        }
        assertThat(deserialized.obj.id).isEqualTo(wireTransaction.id)
    }
}
