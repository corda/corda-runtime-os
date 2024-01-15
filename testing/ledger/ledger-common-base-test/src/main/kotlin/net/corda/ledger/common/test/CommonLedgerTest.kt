package net.corda.ledger.common.test

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.crypto.cipher.suite.merkle.MerkleProofProvider
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.crypto.SignatureSpecServiceImpl
import net.corda.flow.application.services.impl.FlowEngineImpl
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactoryImpl
import net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer
import net.corda.ledger.common.flow.impl.transaction.PrivacySaltProviderServiceImpl
import net.corda.ledger.common.flow.impl.transaction.factory.TransactionMetadataFactoryImpl
import net.corda.ledger.common.flow.impl.transaction.serializer.kryo.WireTransactionKryoSerializer
import net.corda.ledger.common.flow.impl.transaction.TransactionSignatureServiceImpl
import net.corda.ledger.common.flow.impl.transaction.TransactionSignatureVerificationServiceImpl
import net.corda.ledger.common.flow.impl.transaction.factory.TransactionMetadataFactoryImpl
import net.corda.ledger.common.testkit.FakePlatformInfoProvider
import net.corda.ledger.common.testkit.fakePlatformInfoProvider
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import org.mockito.kotlin.mock

abstract class CommonLedgerTest {

    val currentSandboxGroupContext: CurrentSandboxGroupContext = mockCurrentSandboxGroupContext()

    val cipherSchemeMetadata = CipherSchemeMetadataImpl()

    val digestService = DigestServiceImpl(PlatformDigestServiceImpl(cipherSchemeMetadata), null)

    val merkleTreeProvider = MerkleTreeProviderImpl(digestService)

    val jsonMarshallingService = JsonMarshallingServiceImpl(mock<MerkleProofProvider>{})

    val jsonValidator = JsonValidatorImpl()

    val wireTransactionFactory = WireTransactionFactoryImpl(
        merkleTreeProvider, digestService, jsonMarshallingService, jsonValidator
    )

    val flowFiberService = TestFlowFiberServiceWithSerialization(currentSandboxGroupContext)

    val privacySaltProviderService = PrivacySaltProviderServiceImpl(flowFiberService)

    val flowEngine = FlowEngineImpl(flowFiberService)

    val serializationServiceNullCfg = TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)
    val transactionMetadataFactory = TransactionMetadataFactoryImpl(
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
    val transactionSignatureService = TransactionSignatureServiceImpl(
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
