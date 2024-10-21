package net.corda.ledger.common.test

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorOsgiImpl
import net.corda.crypto.cipher.suite.merkle.MerkleProofProvider
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.crypto.SignatureSpecServiceImpl
import net.corda.flow.application.services.impl.FlowEngineImpl
import net.corda.flow.service.FlowCheckpointServiceImpl
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactoryImpl
import net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer
import net.corda.ledger.common.flow.impl.transaction.TransactionSignatureServiceOsgiImpl
import net.corda.ledger.common.flow.impl.transaction.factory.TransactionMetadataFactoryOsgiImpl
import net.corda.ledger.common.flow.transaction.PrivacySaltProviderService
import net.corda.ledger.common.testkit.FakePlatformInfoProvider
import net.corda.ledger.common.testkit.fakePlatformInfoProvider
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.utilities.toByteArray
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import net.corda.ledger.libs.common.flow.impl.transaction.TransactionSignatureVerificationServiceImpl
import net.corda.ledger.libs.common.flow.impl.transaction.kryo.WireTransactionKryoSerializer

abstract class CommonLedgerTest {

    val currentSandboxGroupContext: CurrentSandboxGroupContext = mockCurrentSandboxGroupContext()

    val cipherSchemeMetadata = CipherSchemeMetadataImpl()

    val digestService = DigestServiceImpl(PlatformDigestServiceImpl(cipherSchemeMetadata), null)

    val merkleTreeProvider = MerkleTreeProviderImpl(digestService)

    val jsonMarshallingService = JsonMarshallingServiceImpl(mock<MerkleProofProvider>{})

    val jsonValidator = JsonValidatorOsgiImpl()

    val wireTransactionFactory = WireTransactionFactoryImpl(
        merkleTreeProvider, digestService, jsonMarshallingService, jsonValidator
    )

    private val flowFiberService = TestFlowFiberServiceWithSerialization(currentSandboxGroupContext)

    val flowCheckpointService = FlowCheckpointServiceImpl(flowFiberService)

    val mockPrivacySaltProviderService = mock<PrivacySaltProviderService>().apply {
        whenever(generatePrivacySalt()).thenAnswer {
            PrivacySaltImpl(UUID.randomUUID().toByteArray())
        }
    }

    val flowEngine = FlowEngineImpl(flowFiberService)

    val serializationServiceNullCfg = TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)
    val transactionMetadataFactory = TransactionMetadataFactoryOsgiImpl(
        currentSandboxGroupContext,
        fakePlatformInfoProvider(),
        mockFlowEngine()
    )

    val wireTransactionKryoSerializer = WireTransactionKryoSerializer(wireTransactionFactory)

    val wireTransactionAMQPSerializer = WireTransactionSerializer(wireTransactionFactory)

    val serializationServiceWithWireTx = TestSerializationService.getTestSerializationService({
        it.register(wireTransactionAMQPSerializer, it)
    }, cipherSchemeMetadata)

    private val signatureSpecService = SignatureSpecServiceImpl(cipherSchemeMetadata)
    private val transactionSignatureVerificationServiceImpl = TransactionSignatureVerificationServiceImpl(
        serializationServiceWithWireTx,
        mock<DigitalSignatureVerificationService>(),
        signatureSpecService,
        merkleTreeProvider,
        digestService,
        cipherSchemeMetadata
    )
    val transactionSignatureService = TransactionSignatureServiceOsgiImpl(
        serializationServiceWithWireTx,
        mockSigningService(),
        signatureSpecService,
        merkleTreeProvider,
        FakePlatformInfoProvider(),
        flowEngine,
        transactionSignatureVerificationServiceImpl
    )

    val wireTransactionExample = getWireTransactionExample(digestService, merkleTreeProvider, jsonMarshallingService, jsonValidator)
}
