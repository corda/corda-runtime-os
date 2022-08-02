package net.corda.ledger.consensual

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.MerkleTreeFactoryImpl
import net.corda.flow.application.crypto.SigningServiceImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.ledger.consensual.impl.helper.TestSerializationService
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)

            flowFiberService = MockFlowFiberService(schemeMetadata)
            signingService = SigningServiceImpl(flowFiberService, schemeMetadata)
        }
    }

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val service = ConsensualLedgerServiceImpl(merkleTreeFactory, digestService, signingService, flowFiberService, schemeMetadata)
        val transactionBuilder = service.getTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }
}
