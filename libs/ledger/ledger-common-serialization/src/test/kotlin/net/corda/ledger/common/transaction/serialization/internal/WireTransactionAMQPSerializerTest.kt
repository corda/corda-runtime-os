package net.corda.ledger.common.transaction.serialization.internal

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.testkit.WireTransactionExample.Companion.getWireTransaction
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WireTransactionAMQPSerializerTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var jsonMarshallingService: JsonMarshallingService
        private lateinit var serializationService: SerializationService

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            jsonMarshallingService = JsonMarshallingServiceImpl()
            serializationService = TestSerializationService.getTestSerializationService({
                it.register(WireTransactionSerializer(merkleTreeFactory, digestService, jsonMarshallingService), it)
            }, schemeMetadata)
        }
    }

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val wireTransaction = getWireTransaction(digestService, merkleTreeFactory, jsonMarshallingService)
        val bytes = serializationService.serialize(wireTransaction)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(wireTransaction, deserialized)
        Assertions.assertDoesNotThrow{
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}
