package net.corda.ledger.consensual.flow.impl.transaction.serializer.amqp

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.consensual.test.ConsensualLedgerTest
import net.corda.utilities.serialization.deserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConsensualSignedTransactionSerializerTest: ConsensualLedgerTest() {
    private val serializationService = TestSerializationService.getTestSerializationService({
        it.register(wireTransactionAMQPSerializer, it)
        it.register(consensualSignedTransactionAMQPSerializer, it)
    }, cipherSchemeMetadata)

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val bytes = serializationService.serialize(consensualSignedTransactionExample)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(consensualSignedTransactionExample, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(consensualSignedTransactionExample.id, deserialized.id)
    }
}