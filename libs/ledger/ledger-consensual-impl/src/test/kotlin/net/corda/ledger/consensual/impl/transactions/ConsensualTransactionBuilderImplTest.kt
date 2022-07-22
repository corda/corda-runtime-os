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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant
import kotlin.random.Random

internal class ConsensualTransactionBuilderImplTest{
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var secureRandom: SecureRandom
        private lateinit var serializer: SerializationService

        val metadata = ConsensualTransactionMetaDataImpl("ConsensualLedger", "v0.01", emptyList())
        val memberX500Name = MemberX500Name("R3", "London", "GB")
        val consensualState = TestConsensualState("test", listOf(PartyImpl(memberX500Name, mockPublicKey())))

        class TestConsensualState(
            val testField: String,
            override val participants: List<Party>
        ) : ConsensualState

        private fun mockPublicKey(): PublicKey {
            val serialisedPublicKey = Random(Instant.now().toEpochMilli()).nextBytes(256)
            return mock {
                on { encoded } doReturn serialisedPublicKey
            }
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            serializer = TestSerializationService.getTestSerializationService(schemeMetadata)
        }
    }

    @Test
    fun `can build a simple Transaction`() {
        ConsensualTransactionBuilderImpl()
            .withMetadata(metadata)
            .withTimeStamp(Instant.now())
            .withConsensualState(consensualState)
            .build(merkleTreeFactory, digestService, secureRandom, serializer)
    }

    @Test
    fun `cannot build Transaction without Metadata`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl()
                .withTimeStamp(Instant.now())
                .withConsensualState(consensualState)
                .build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
        assertEquals("Null metadata is not allowed", exception.message)
    }

    @Test
    fun `cannot build Transaction without TimeStamp`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl()
                .withMetadata(metadata)
                .withConsensualState(consensualState)
                .build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
        assertEquals("Null timeStamp is not allowed", exception.message)
    }

    @Test
    fun `cannot build Transaction without Consensual States`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl()
                .withMetadata(metadata)
                .withTimeStamp(Instant.now())
                .build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
        assertEquals("At least one Consensual State is required", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl()
                .withMetadata(metadata)
                .withTimeStamp(Instant.now())
                .withConsensualState(consensualState)
                .withConsensualState(TestConsensualState("test", emptyList()))
                .build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
        assertEquals("All consensual states needs to have participants", exception.message)
    }


}