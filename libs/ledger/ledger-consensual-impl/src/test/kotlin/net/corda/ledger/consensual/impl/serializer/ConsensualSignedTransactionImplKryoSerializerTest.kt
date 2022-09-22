package net.corda.ledger.consensual.impl.serializer

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.serializer.WireTransactionKryoSerializer
import net.corda.ledger.common.transaction.serialization.internal.WireTransactionSerializer
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.impl.transaction.serializer.ConsensualSignedTransactionImplKryoSerializer
import net.corda.ledger.consensual.testkit.ConsensualSignedTransactionImplExample.Companion.getConsensualSignedTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ConsensualSignedTransactionImplKryoSerializerTest {
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
    fun `serialization of a Wire Tx object using the kryo default serialization`() {
        val wireTransactionKryoSerializer = WireTransactionKryoSerializer(
            merkleTreeFactory,
            digestService,
            jsonMarshallingService
        )
        val consensualSignedTransactionImplKryoSerializer = ConsensualSignedTransactionImplKryoSerializer(
            serializationService
        )

        val signedTransaction = getConsensualSignedTransactionImpl(
            digestService,
            merkleTreeFactory,
            serializationService,
            jsonMarshallingService
        )

        val serializer = createCheckpointSerializer(
            mapOf(
                WireTransaction::class.java to wireTransactionKryoSerializer,
                ConsensualSignedTransactionImpl::class.java to consensualSignedTransactionImplKryoSerializer
            ),
            emptyList(),
            setOf(
                PrivacySaltImpl::class.java,
                DigitalSignatureAndMetadata::class.java,
                signedTransaction.signatures[0].by::class.java,
                emptyMap<String, String>()::class.java,
                DigitalSignature.WithKey::class.java
            )
        )
        val bytes = serializer.serialize(signedTransaction)
        val deserialized = serializer.deserialize(bytes, ConsensualSignedTransactionImpl::class.java)

        assertThat(deserialized).isEqualTo(signedTransaction)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(signedTransaction.id, deserialized.id)
    }
}