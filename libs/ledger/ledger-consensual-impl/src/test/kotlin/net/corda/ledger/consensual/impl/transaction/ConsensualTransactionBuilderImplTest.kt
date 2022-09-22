package net.corda.ledger.consensual.impl.transaction

import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import kotlin.test.assertIs
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.impl.executor.ExternalEventExecutorImpl
import net.corda.flow.fiber.FlowFiberServiceImpl
import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.ledger.consensual.impl.helper.ConfiguredTestSerializationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class ConsensualTransactionBuilderImplTest{
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var secureRandom: SecureRandom
        private lateinit var serializer: SerializationService
        private lateinit var signingService: SigningService
        private lateinit var externalEventExecutor: ExternalEventExecutor
        private lateinit var testPublicKey: PublicKey
        private lateinit var testConsensualState: ConsensualState
        private lateinit var keyEncodingService: KeyEncodingService

        private val testMemberX500Name = MemberX500Name("R3", "London", "GB")

        class TestConsensualState(
            val testField: String,
            override val participants: List<Party>
        ) : ConsensualState {
            override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            serializer = ConfiguredTestSerializationService.getTestSerializationService(schemeMetadata)

            val flowFiberService = FlowFiberServiceImpl()
            externalEventExecutor = ExternalEventExecutorImpl(flowFiberService)
            keyEncodingService = CipherSchemeMetadataImpl()
            signingService = SigningServiceImpl(externalEventExecutor, keyEncodingService)

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
        val tx = ConsensualTransactionBuilderImpl(merkleTreeFactory, digestService, secureRandom, serializer, signingService)
            .withStates(testConsensualState)
            .signInitial(testPublicKey)
        assertIs<SecureHash>(tx.id)
    }

    @Test
    fun `cannot build Transaction without Consensual States`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl(merkleTreeFactory, digestService, secureRandom, serializer, signingService)
                .signInitial(testPublicKey)
        }
        assertEquals("At least one Consensual State is required", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl(merkleTreeFactory, digestService, secureRandom, serializer, signingService)
                .withStates(testConsensualState)
                .withStates(TestConsensualState("test", emptyList()))
                .signInitial(testPublicKey)
        }
        assertEquals("All consensual states needs to have participants", exception.message)
    }
}