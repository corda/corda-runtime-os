package net.corda.ledger.consensual

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.ledger.consensual.impl.ConsensualLedgerServiceImpl
import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.ledger.consensual.impl.helper.TestSerializationService
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertIs

// TODO(This does not look too healthy... Parts of it came from net.corda.flow.application.services.MockFlowFiberService)
class MockFlowFiberService(private val schemeMetadata: CipherSchemeMetadata) : FlowFiberService {
    private val mockFlowFiber: FlowFiber

    init{
        val serializer = TestSerializationService.getTestSerializationService(schemeMetadata)
        val mockFlowSandboxGroupContext = mock<FlowSandboxGroupContext>()
        whenever (mockFlowSandboxGroupContext.amqpSerializer).thenReturn(serializer)

        val membershipGroupReader: MembershipGroupReader = mock()
        val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
        val holdingIdentity =  HoldingIdentity(BOB_X500_NAME,"group1")
        val flowFiberExecutionContext = FlowFiberExecutionContext(
            mock(),
            mockFlowSandboxGroupContext,
            holdingIdentity,
            membershipGroupReader
        )

        mockFlowFiber = mock {
            on { it.getExecutionContext() }.thenReturn(flowFiberExecutionContext)
        }

    }
    override fun getExecutingFiber(): FlowFiber {
        return mockFlowFiber
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
            override fun verify(ledgerTransaction: ConsensualLedgerTransaction): Boolean = true
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)

            flowFiberService = MockFlowFiberService(schemeMetadata)
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
            .withTimestamp(Instant.now())
            .withStates(testConsensualState)
            .signInitial(testPublicKey)
        assertIs<ConsensualSignedTransaction>(signedTransaction)
    }
}
