package net.corda.ledger.consensual.impl.transactions

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.MerkleTreeFactoryImpl
import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.ledger.consensual.impl.helper.TestSerializationService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant

internal class ConsensualTransactionBuilderImplTest{
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var secureRandom: SecureRandom
        private lateinit var serializer: SerializationService
        private lateinit var testPublicKey: PublicKey
        private lateinit var testConsensualState: ConsensualState

        private val testMemberX500Name = MemberX500Name("R3", "London", "GB")

        class TestConsensualState(
            val testField: String,
            override val participants: List<Party>
        ) : ConsensualState

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            serializer = TestSerializationService.getTestSerializationService(schemeMetadata)

            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(512) // Shortest possible to not slow down tests.
            testPublicKey = kpg.genKeyPair().public

            testConsensualState = TestConsensualState(
                "test",
                listOf(PartyImpl(testMemberX500Name, testPublicKey))
            )
        }
    }

    @Test
    fun `can build a simple Transaction`() {
        ConsensualTransactionBuilderImpl()
            .withTimeStamp(Instant.now())
            .withConsensualState(testConsensualState)
            .build(merkleTreeFactory, digestService, secureRandom, serializer)
    }

    @Test
    fun `cannot build Transaction without TimeStamp`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl()
                .withConsensualState(testConsensualState)
                .build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
        assertEquals("Null timeStamp is not allowed", exception.message)
    }

    @Test
    fun `cannot build Transaction without Consensual States`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl()
                .withTimeStamp(Instant.now())
                .build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
        assertEquals("At least one Consensual State is required", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl()
                .withTimeStamp(Instant.now())
                .withConsensualState(testConsensualState)
                .withConsensualState(TestConsensualState("test", emptyList()))
                .build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
        assertEquals("All consensual states needs to have participants", exception.message)
    }
}