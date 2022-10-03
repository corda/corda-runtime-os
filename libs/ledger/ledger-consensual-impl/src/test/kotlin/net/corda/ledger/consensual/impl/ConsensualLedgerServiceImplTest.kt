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
    private val jsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val flowFiberService = TestFlowFiberServiceWithSerializationProxy(cipherSchemeMetadata)
    private val serializationService: SerializationService = SerializationServiceImpl(flowFiberService)

// check merge:
        private val serializationService = TestSerializationService.getTestSerializationService({
            it.register(PartySerializer(), it)
        }, schemeMetadata)

    private val consensualTransactionBuilderFactory: ConsensualTransactionBuilderFactory =
        ConsensualTransactionBuilderFactoryImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            ConsensualTransactionMocks.mockSigningService(),
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
        val service = ConsensualLedgerServiceImpl(consensualTransactionBuilderFactory, flowEngine)
        val transactionBuilder = service.getTransactionBuilder()
        val signedTransaction = transactionBuilder
            .withStates(ConsensualTransactionMocks.testConsensualState)
            .signInitial(ConsensualTransactionMocks.testPublicKey)
        assertIs<ConsensualSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)
    }
}
