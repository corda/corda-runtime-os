package net.corda.ledger.common.data.transaction.serializer.amqp

import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.utilities.serialization.deserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WireTransactionAMQPSerializerTest: CommonLedgerTest() {
    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val bytes = serializationServiceWithWireTx.serialize(wireTransactionExample)
        val deserialized = serializationServiceWithWireTx.deserialize(bytes)
        assertEquals(wireTransactionExample, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }

        assertEquals(wireTransactionExample.id, deserialized.id)
    }
}
