package net.corda.ledger.consensual.impl.transaction

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant
import kotlin.math.abs
import kotlin.test.assertIs
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.impl.executor.ExternalEventExecutorImpl
import net.corda.flow.fiber.FlowFiberServiceImpl
import net.corda.ledger.consensual.impl.ConsensualTransactionMocks
import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.ledger.consensual.impl.helper.ConfiguredTestSerializationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

// TODO(deduplicate boilerplate with ConsensualTransactionBuilderImplTest)
internal class ConsensualLedgerTransactionImplTest{
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeProvider: MerkleTreeProvider
        private lateinit var secureRandom: SecureRandom
        private lateinit var serializer: SerializationService
        private lateinit var signingService: SigningService
        private lateinit var jsonMarshallingService: JsonMarshallingService

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
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is TestConsensualState) return false
                if (other.testField != testField) return false
                if (other.participants.size != participants.size) return false
                return other.participants.containsAll(participants)
            }
            override fun hashCode(): Int = testField.hashCode() + participants.hashCode() * 31
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom
            merkleTreeProvider = MerkleTreeProviderImpl(digestService)
            serializer = ConfiguredTestSerializationService.getTestSerializationService(schemeMetadata)
            jsonMarshallingService = JsonMarshallingServiceImpl()

            val flowFiberService = FlowFiberServiceImpl()
            externalEventExecutor = ExternalEventExecutorImpl(flowFiberService)
            keyEncodingService = CipherSchemeMetadataImpl()
            signingService = SigningServiceImpl(externalEventExecutor, keyEncodingService)

            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(512) // Shortest possible to not slow down tests.
            testPublicKey = kpg.genKeyPair().public

            testConsensualState = TestConsensualState("test", listOf(PartyImpl(testMemberX500Name, testPublicKey)))
        }
    }

    @Test
    fun `ledger transaction contains the same data what it was created with`() {
        val testTimestamp = Instant.now()
        val signedTransaction = ConsensualTransactionBuilderImpl(
            merkleTreeProvider,
            digestService,
            secureRandom,
            serializer,
            signingService,
            jsonMarshallingService,
            ConsensualTransactionMocks.mockMemberLookup(),
            ConsensualTransactionMocks.mockSandboxCpks(),
        )
            .withStates(testConsensualState)
            .signInitial(testPublicKey)
        val ledgerTransaction = signedTransaction.toLedgerTransaction()
        assertTrue(abs(ledgerTransaction.timestamp.toEpochMilli()/1000 - testTimestamp.toEpochMilli()/1000) < 5 )
        assertIs<List<ConsensualState>>(ledgerTransaction.states)
        assertEquals(1, ledgerTransaction.states.size)
        assertEquals(testConsensualState, ledgerTransaction.states.first())
        assertIs<TestConsensualState>(ledgerTransaction.states.first())

        assertIs<SecureHash>(ledgerTransaction.id)
    }
}
