package net.corda.ledger.common.data.transaction.serializer.internal

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactoryImpl
import net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.v5.application.serialization.deserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WireTransactionAMQPSerializerTest {
    companion object {
        private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
        val digestService = DigestServiceImpl(cipherSchemeMetadata, null)
        val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
        val jsonMarshallingService = JsonMarshallingServiceImpl()
        private val serializationServiceBasic =
            TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)
        private val flowFiberService = TestFlowFiberServiceWithSerialization()
        private val wireTransactionFactory = WireTransactionFactoryImpl(
            merkleTreeProvider, digestService, jsonMarshallingService, cipherSchemeMetadata,
            serializationServiceBasic, flowFiberService
        )
        val serializationService = TestSerializationService.getTestSerializationService({
            it.register(WireTransactionSerializer(wireTransactionFactory), it)
        }, cipherSchemeMetadata)
    }

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
