package net.corda.ledger.consensual.flow.impl

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.serialization.SerializationServiceImpl
import net.corda.flow.application.services.FlowEngineImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.ledger.common.testkit.mockPlatformInfoProvider
import net.corda.ledger.common.testkit.mockSigningService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualTransactionBuilderFactory
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualTransactionBuilderFactoryImpl
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertIs

class TestFlowFiberServiceWithSerializationProxy constructor(
    private val cipherSchemeMetadata: CipherSchemeMetadata
) : FlowFiberService, SingletonSerializeAsToken {
    override fun getExecutingFiber(): FlowFiber {
        val testFlowFiberServiceWithSerialization = TestFlowFiberServiceWithSerialization()
        testFlowFiberServiceWithSerialization.configureSerializer({}, cipherSchemeMetadata)
        return testFlowFiberServiceWithSerialization.getExecutingFiber()
    }
}

class ConsensualLedgerServiceImplTest {
    private val jsonMarshallingService = JsonMarshallingServiceImpl()
    private val jsonValidator = JsonValidatorImpl()
    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val flowFiberService = TestFlowFiberServiceWithSerializationProxy(cipherSchemeMetadata)
    private val flowEngine: FlowEngine = FlowEngineImpl(flowFiberService)
    private val serializationService: SerializationService = SerializationServiceImpl(flowFiberService)

    private val consensualTransactionBuilderFactory: ConsensualTransactionBuilderFactory =
        ConsensualTransactionBuilderFactoryImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            jsonValidator,
            merkleTreeProvider,
            serializationService,
            mockSigningService(),
            mock(),
            mockPlatformInfoProvider(),
            flowFiberService
        )


    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val service = ConsensualLedgerServiceImpl(consensualTransactionBuilderFactory, flowEngine, mock())
        val transactionBuilder = service.getTransactionBuilder()
        assertIs<ConsensualTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `ConsensualLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        val service = ConsensualLedgerServiceImpl(consensualTransactionBuilderFactory, flowEngine, mock())
        val transactionBuilder = service.getTransactionBuilder()
        val signedTransaction = transactionBuilder
            .withStates(consensualStateExample)
            .sign(publicKeyExample)
        assertIs<ConsensualSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)
    }
}
