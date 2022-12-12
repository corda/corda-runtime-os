package net.corda.ledger.common.data.transaction.serializer.amqp.tests

import net.corda.ledger.common.integration.test.CommonLedgerIntegrationTest
import net.corda.v5.application.serialization.deserialize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class WireTransactionSerializerTest: CommonLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a wireTransaction`() {
        val serialised =  serializationService.serialize(wireTransaction)

        // Perform deserialization and check if the correct class is deserialized
        val deserialized =
            serializationService.deserialize(serialised)

        assertThat(deserialized.javaClass.name).isEqualTo(
            "net.corda.ledger.common.data.transaction.WireTransaction"
        )

        assertThat(deserialized).isEqualTo(wireTransaction)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertThat(deserialized.id).isEqualTo(wireTransaction.id)
    }
}
