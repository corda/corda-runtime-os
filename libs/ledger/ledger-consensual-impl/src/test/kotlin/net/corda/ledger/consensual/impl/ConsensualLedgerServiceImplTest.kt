package net.corda.ledger.consensual.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PublicKey
import kotlin.test.assertIs

class TestFlowFiberServiceWithSerializationProxy constructor(
    private val schemeMetadata: CipherSchemeMetadata
) : FlowFiberService, SingletonSerializeAsToken {
    override fun getExecutingFiber(): FlowFiber {
        val testFlowFiberServiceWithSerialization = TestFlowFiberServiceWithSerialization()
        testFlowFiberServiceWithSerialization.configureSerializer ({
            it.register(PartySerializer(), it)
        }, schemeMetadata)
        return testFlowFiberServiceWithSerialization.getExecutingFiber()
    }
}

class ConsensualLedgerServiceImplTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var signingService: SigningService
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var flowFiberService: FlowFiberService

        private lateinit var testPublicKey: PublicKey
        private lateinit var testConsensualState: ConsensualState

        class TestConsensualState(
            val testField: String,
            override val participants: List<Party>
        ) : ConsensualState {
            override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)

            flowFiberService = TestFlowFiberServiceWithSerializationProxy(schemeMetadata)
            signingService = SigningServiceImpl(flowFiberService, schemeMetadata)

            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(512) // Shortest possible to not slow down tests.
            testPublicKey = kpg.genKeyPair().public

            val testMemberX500Name = MemberX500Name("R3", "London", "GB")

            testConsensualState =
                TestConsensualState(
                    "test",
                    listOf(
                        PartyImpl(
                            testMemberX500Name,
                            testPublicKey
                        )
                    )
                )
        }
    }

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val service = ConsensualLedgerServiceImpl(merkleTreeFactory, digestService, signingService, flowFiberService, schemeMetadata)
        val transactionBuilder = service.getTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `ConsensualLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        val service = ConsensualLedgerServiceImpl(merkleTreeFactory, digestService, signingService, flowFiberService, schemeMetadata)
        val transactionBuilder = service.getTransactionBuilder()
        val signedTransaction = transactionBuilder
            .withStates(testConsensualState)
            .signInitial(testPublicKey)
        assertIs<ConsensualSignedTransaction>(signedTransaction)
    }
}
