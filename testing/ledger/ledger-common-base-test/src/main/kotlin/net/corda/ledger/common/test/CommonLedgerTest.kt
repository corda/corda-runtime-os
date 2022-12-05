package net.corda.ledger.common.test

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.services.FlowEngineImpl
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactoryImpl
import net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer
import net.corda.ledger.common.flow.impl.transaction.factory.TransactionMetadataFactoryImpl
import net.corda.ledger.common.flow.impl.transaction.serializer.kryo.WireTransactionKryoSerializer
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.common.testkit.mockPlatformInfoProvider
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext

abstract class CommonLedgerTest {

    val currentSandboxGroupContext: CurrentSandboxGroupContext = mockCurrentSandboxGroupContext()

    val cipherSchemeMetadata = CipherSchemeMetadataImpl()

    val digestService = DigestServiceImpl(PlatformDigestServiceImpl(cipherSchemeMetadata), null)

    val merkleTreeProvider = MerkleTreeProviderImpl(digestService)

    val jsonMarshallingService = JsonMarshallingServiceImpl()

    val jsonValidator = JsonValidatorImpl()

    val wireTransactionFactory = WireTransactionFactoryImpl(
        merkleTreeProvider, digestService, jsonMarshallingService, jsonValidator, cipherSchemeMetadata
    )

    val flowFiberService = TestFlowFiberServiceWithSerialization(currentSandboxGroupContext)

    val flowEngine = FlowEngineImpl(flowFiberService)

    val serializationServiceNullCfg = TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)
    val transactionMetadataFactory = TransactionMetadataFactoryImpl(currentSandboxGroupContext, mockPlatformInfoProvider())

    val wireTransactionKryoSerializer = WireTransactionKryoSerializer(wireTransactionFactory)

    val wireTransactionAMQPSerializer = WireTransactionSerializer(wireTransactionFactory)

    val serializationServiceWithWireTx = TestSerializationService.getTestSerializationService({
        it.register(wireTransactionAMQPSerializer, it)
    }, cipherSchemeMetadata)

    val wireTransactionExample = getWireTransactionExample(digestService, merkleTreeProvider, jsonMarshallingService, jsonValidator)
}
