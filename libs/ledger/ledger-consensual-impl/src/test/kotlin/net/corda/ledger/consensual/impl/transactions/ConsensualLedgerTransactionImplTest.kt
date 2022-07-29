package net.corda.ledger.consensual.impl.transactions

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.MerkleTreeFactoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.fiber.FlowFiberServiceImpl
import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.ledger.consensual.impl.helper.TestSerializationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant
import kotlin.test.assertIs

// TODO(deduplicate boilerplate with ConsensualTransactionBuilderImplTest)
internal class ConsensualLedgerTransactionImplTest{
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var secureRandom: SecureRandom
        private lateinit var serializer: SerializationService
        private lateinit var signingService: SigningService
        private lateinit var testPublicKey: PublicKey
        private lateinit var testConsensualState: ConsensualState

        private val testMemberX500Name = MemberX500Name("R3", "London", "GB")

        class TestConsensualState(
            val testField: String,
            override val participants: List<Party>
        ) : ConsensualState {
            override fun verify(consensualLedgerTransaction: ConsensualLedgerTransaction): Boolean = true
            override fun equals(other: Any?): Boolean =
                other === this ||
                        other is TestConsensualState &&
                        other.testField == testField &&
                        other.participants == participants
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            serializer = TestSerializationService.getTestSerializationService(schemeMetadata)

            val flowFiberService = FlowFiberServiceImpl()
            signingService = SigningServiceImpl(flowFiberService, schemeMetadata)

            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(512) // Shortest possible to not slow down tests.
            testPublicKey = kpg.genKeyPair().public

            testConsensualState = TestConsensualState("test", listOf(PartyImpl(testMemberX500Name, testPublicKey)))
        }
    }

    @Test
    fun `ledger transaction contains the same data what it was created with`() {
        val testTimestamp = Instant.now()
        val signedTransaction = ConsensualTransactionBuilderImpl(merkleTreeFactory, digestService, secureRandom, serializer, signingService)
            .withTimeStamp(testTimestamp)
            .withConsensualState(testConsensualState)
            .signInitial(testPublicKey)
        val ledgerTransaction = ConsensualLedgerTransactionImpl(signedTransaction.wireTransaction, serializer)
        assertEquals(testTimestamp, ledgerTransaction.timestamp)
        assertIs<List<ConsensualState>>(ledgerTransaction.consensualStates)
        assertEquals(1, ledgerTransaction.consensualStates.size)
        assertEquals(testConsensualState, ledgerTransaction.consensualStates.first())
        assertIs<TestConsensualState>(ledgerTransaction.consensualStates.first())
    }
}