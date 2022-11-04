package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.v5.application.serialization.deserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UtxoSignedTransactionSerializerTest: UtxoLedgerTest() {
    private val serializationService = TestSerializationService.getTestSerializationService({
        it.register(wireTransactionAMQPSerializer, it)
        it.register(utxoSignedTransactionAMQPSerializer, it)
    }, cipherSchemeMetadata)

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val bytes = serializationService.serialize(utxoSignedTransactionExample)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(utxoSignedTransactionExample, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(utxoSignedTransactionExample.id, deserialized.id)
    }
}