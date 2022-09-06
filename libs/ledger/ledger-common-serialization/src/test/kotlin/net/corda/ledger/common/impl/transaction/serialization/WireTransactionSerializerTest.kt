package net.corda.ledger.common.impl.transaction.serialization

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WireTransactionSerializerTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var serializerFactory: SerializerFactory

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            serializerFactory = TestSerializationService.getTestDefaultFactoryNoEvolution({
                it.register(WireTransactionSerializer(merkleTreeFactory, digestService), it)
            }, schemeMetadata)
        }
    }

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())
        val componentGroupLists = listOf(
            listOf("123".toByteArray(), "45678".toByteArray()),
            listOf(".".toByteArray()),
            listOf("abc d efg".toByteArray()),
        )
        val wireTransaction = WireTransaction(merkleTreeFactory, digestService, privacySalt, componentGroupLists)
        val bytes = SerializationOutput(serializerFactory).serialize(wireTransaction, testSerializationContext)
        val deserialized = DeserializationInput(serializerFactory).deserialize(bytes, testSerializationContext)
        assertEquals(wireTransaction, deserialized)
    }
}
