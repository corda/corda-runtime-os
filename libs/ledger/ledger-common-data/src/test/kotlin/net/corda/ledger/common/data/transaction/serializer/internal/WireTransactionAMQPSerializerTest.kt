package net.corda.ledger.common.data.transaction.serializer.internal

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer
import net.corda.ledger.common.test.LedgerTest
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.v5.application.serialization.deserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WireTransactionAMQPSerializerTest: LedgerTest() {
    private val serializationService = TestSerializationService.getTestSerializationService({
        it.register(WireTransactionSerializer(wireTransactionFactory), it)
    }, cipherSchemeMetadata)

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val wireTransaction = getWireTransactionExample(digestService, merkleTreeProvider, jsonMarshallingService)
        val bytes = serializationService.serialize(wireTransaction)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(wireTransaction, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }

        assertEquals(wireTransaction.id, deserialized.id)
    }
}
