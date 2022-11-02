package net.corda.ledger.common.data.transaction.serializer.internal

import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.v5.application.serialization.deserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WireTransactionAMQPSerializerTest: CommonLedgerTest() {
    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val bytes = serializationServiceWithWireTx.serialize(wireTransaction)
        val deserialized = serializationServiceWithWireTx.deserialize(bytes)
        assertEquals(wireTransaction, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }

        assertEquals(wireTransaction.id, deserialized.id)
    }
}
