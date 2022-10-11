package net.corda.ledger.consensual.impl

import java.security.KeyPairGenerator
import java.security.PublicKey
import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.serialization.SerializationServiceImpl
import net.corda.flow.application.services.FlowEngineImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.ledger.consensual.impl.transaction.factory.ConsensualTransactionBuilderFactory
import net.corda.ledger.consensual.impl.transaction.factory.ConsensualTransactionBuilderFactoryImpl
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class TestFlowFiberServiceWithSerializationProxy constructor(
    private val schemeMetadata: CipherSchemeMetadata
) : FlowFiberService, SingletonSerializeAsToken {
    override fun getExecutingFiber(): FlowFiber {
        val testFlowFiberServiceWithSerialization = TestFlowFiberServiceWithSerialization()
        testFlowFiberServiceWithSerialization.configureSerializer({
                                                                      it.register(PartySerializer(), it)
                                                                  }, schemeMetadata)
        return testFlowFiberServiceWithSerialization.getExecutingFiber()
    }
}

class ConsensualLedgerServiceImplTest {
    companion object {
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

    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeProvider: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val flowFiberService: FlowFiberService =
        TestFlowFiberServiceWithSerializationProxy(cipherSchemeMetadata)
    private val flowEngine: FlowEngine = FlowEngineImpl(flowFiberService)
    private val signingService: SigningService = mock()
    private val serializationService: SerializationService = SerializationServiceImpl(flowFiberService)
    private val consensualTransactionBuilderFactory: ConsensualTransactionBuilderFactory =
        ConsensualTransactionBuilderFactoryImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            signingService,
            ConsensualTransactionMocks.mockMemberLookup(),
            flowFiberService
        )


    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val service = ConsensualLedgerServiceImpl(consensualTransactionBuilderFactory, flowEngine)
        val transactionBuilder = service.getTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `ConsensualLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        whenever(signingService.sign(any(), any(), any())).thenReturn(
            DigitalSignature.WithKey(
                testPublicKey,
                byteArrayOf(1),
                emptyMap()
            )
        )
        val service = ConsensualLedgerServiceImpl(consensualTransactionBuilderFactory, flowEngine)
        val transactionBuilder = service.getTransactionBuilder()
        val signedTransaction = transactionBuilder
            .withStates(testConsensualState)
            .signInitial(testPublicKey)
        assertIs<ConsensualSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)
    }
}
