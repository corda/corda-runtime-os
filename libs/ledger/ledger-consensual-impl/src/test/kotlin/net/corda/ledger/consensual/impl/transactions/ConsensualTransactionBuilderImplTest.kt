package net.corda.ledger.consensual.impl.transactions

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.MerkleTreeFactoryImpl
import net.corda.ledger.consensual.impl.helper.TestSerializationService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.time.Instant


internal class ConsensualTransactionBuilderImplTest{
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var secureRandom: SecureRandom
        private val serializer: SerializationService = TestSerializationService.getTestSerializationService()

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)


        }
    }
    @Test
    fun `cannot build empty Transaction`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl().build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
    }
    @Test
    fun `can build a simple Transaction`() {
        val tx = ConsensualTransactionBuilderImpl()
            .withTimeStamp(Instant.now())
            .withMetadata(ConsensualTransactionMetaDataImpl("ConsensualLedger", "v0.01", emptyList()))
            .build(merkleTreeFactory, digestService, secureRandom, serializer)
        println(tx.id)
    }
}