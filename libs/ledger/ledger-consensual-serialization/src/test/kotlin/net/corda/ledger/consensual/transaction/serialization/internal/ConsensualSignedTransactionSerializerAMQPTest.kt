package net.corda.ledger.consensual.transaction.serialization.internal

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.transaction.serialization.internal.WireTransactionSerializer
import net.corda.ledger.consensual.testkit.getConsensualSignedTransaction
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConsensualSignedTransactionSerializerAMQPTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeProvider: MerkleTreeProvider
        private lateinit var jsonMarshallingService: JsonMarshallingService
        private lateinit var serializationService: SerializationService

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeProvider = MerkleTreeProviderImpl(digestService)
            jsonMarshallingService = JsonMarshallingServiceImpl()

            val serializationServiceNullCfg = TestSerializationService.getTestSerializationService({}, schemeMetadata)
            serializationService = TestSerializationService.getTestSerializationService({
                it.register(WireTransactionSerializer(merkleTreeProvider, digestService, jsonMarshallingService), it)
                it.register(ConsensualSignedTransactionSerializer(serializationServiceNullCfg), it)
            }, schemeMetadata)
        }
    }

    @Test
    fun `Should serialize and then deserialize wire Tx`() {

        val signedTransaction = getConsensualSignedTransaction(
            digestService,
            merkleTreeProvider,
            serializationService,
            jsonMarshallingService
        )

        val bytes = serializationService.serialize(signedTransaction)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(signedTransaction, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(signedTransaction.id, deserialized.id)
    }
}
