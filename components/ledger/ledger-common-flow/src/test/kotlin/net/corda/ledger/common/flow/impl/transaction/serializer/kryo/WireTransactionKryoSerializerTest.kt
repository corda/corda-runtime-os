package net.corda.ledger.common.flow.impl.transaction.serializer.kryo

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.testkit.getWireTransaction
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WireTransactionKryoSerializerTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeProvider: MerkleTreeProvider
        private lateinit var jsonMarshallingService: JsonMarshallingService

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeProvider = MerkleTreeProviderImpl(digestService)
            jsonMarshallingService = JsonMarshallingServiceImpl()
        }
    }

    @Test
    fun `serialization of a Wire Tx object using the kryo default serialization`() {
        val wireTransaction = getWireTransaction(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService
        )
        val wireTransactionKryoSerializer = WireTransactionKryoSerializer(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService
        )

        val serializer = createCheckpointSerializer(
            serializers = mapOf(WireTransaction::class.java to wireTransactionKryoSerializer),
            singletonInstances = emptyList(),
            extraClasses = setOf(PrivacySalt::class.java, PrivacySaltImpl::class.java)
        )

        val bytes = serializer.serialize(wireTransaction)
        val deserialized = serializer.deserialize(bytes, WireTransaction::class.java)

        assertThat(deserialized).isEqualTo(wireTransaction)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}