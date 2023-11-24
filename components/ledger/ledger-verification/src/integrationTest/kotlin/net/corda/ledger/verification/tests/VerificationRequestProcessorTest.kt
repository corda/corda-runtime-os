package net.corda.ledger.verification.tests

import net.corda.common.json.validation.JsonValidator
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.parseSecureHash
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.utxo.verification.TransactionVerificationResponse
import net.corda.ledger.utxo.verification.TransactionVerificationStatus
import net.corda.ledger.verification.processor.impl.VerificationRequestHandlerImpl
import net.corda.ledger.verification.processor.impl.VerificationRequestProcessor
import net.corda.ledger.verification.tests.helpers.VirtualNodeService
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.membership.lib.GroupParametersFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.ContractVerificationException
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class VerificationRequestProcessorTest {
    private companion object {
        const val TEST_CPB = "/META-INF/ledger-utxo-demo-app.cpb"
        const val VERIFICATION_ERROR_MESSAGE = "Output state has invalid field value"
        const val TIMEOUT_MILLIS = 10000L

        val NOTARY_X500_NAME = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
        val PUBLIC_KEY: PublicKey = KeyPairGenerator.getInstance("RSA")
            .also {
                it.initialize(512)
            }.genKeyPair().public
        val REQUEST_ID = UUID.randomUUID().toString()
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext(
            REQUEST_ID, "flow id", KeyValuePairList(listOf(KeyValuePair("corda.account", "test account")))
        )
        val NON_EXISTING_CPK = CordaPackageSummary(
            "NonExistingCPK",
            "1.0",
            "SHA-256:2222222222222222",
            "SHA-256:3333333333333333"
        )
    }

    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()
    private lateinit var virtualNodeService: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var deserializer: CordaAvroDeserializer<TransactionVerificationResponse>
    private lateinit var wireTransactionFactory: WireTransactionFactory
    private lateinit var jsonMarshallingService: JsonMarshallingService
    private lateinit var jsonValidator: JsonValidator
    private lateinit var keyValueSerializer: CordaAvroSerializer<KeyValuePairList>

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var externalEventResponseFactory: ExternalEventResponseFactory

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var groupParametersFactory: GroupParametersFactory

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var currentSandboxGroupContext: CurrentSandboxGroupContext

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            virtualNodeService = setup.fetchService(TIMEOUT_MILLIS)
            wireTransactionFactory = setup.fetchService(TIMEOUT_MILLIS)
            jsonMarshallingService = setup.fetchService(TIMEOUT_MILLIS)
            jsonValidator = setup.fetchService(TIMEOUT_MILLIS)
        }
        deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, TransactionVerificationResponse::class.java)
        keyValueSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
    }

    @Test
    fun `successfully verifies transaction contracts`() {
        val virtualNodeInfo = virtualNodeService.load(TEST_CPB)
        val holdingIdentity = virtualNodeInfo.holdingIdentity
        val cpksMetadata =
            cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier)!!.cpksMetadata.filter { it.isContractCpk() }
        val cpkSummaries = cpksMetadata.map { it.toCpkSummary() }
        val verificationSandboxService = virtualNodeService.verificationSandboxService
        val sandbox = verificationSandboxService.get(holdingIdentity, cpkSummaries)
        val transaction = createTestTransaction(sandbox, isValid = true)
        val request = createRequest(sandbox, holdingIdentity, transaction, cpkSummaries)

        // Create request processor
        val processor = VerificationRequestProcessor(
            currentSandboxGroupContext,
            verificationSandboxService,
            VerificationRequestHandlerImpl(externalEventResponseFactory),
            externalEventResponseFactory,
            TransactionVerificationRequest::class.java,
            FlowEvent::class.java
        )

        // Send request to message processor
        val flowEvent = processor.process(request)

        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNull()
        assertThat(response.requestId).isEqualTo(REQUEST_ID)

        val transactionVerificationResponse = deserializer.deserialize(response.payload.array())!!
        assertThat(transactionVerificationResponse.verificationStatus).isEqualTo(TransactionVerificationStatus.VERIFIED)
        assertThat(transactionVerificationResponse.verificationFailure).isNull()
    }

    @Test
    fun `unsuccessfully verifies transaction contracts`() {
        val virtualNodeInfo = virtualNodeService.load(TEST_CPB)
        val holdingIdentity = virtualNodeInfo.holdingIdentity
        val cpksMetadata =
            cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier)!!.cpksMetadata.filter { it.isContractCpk() }
        val cpkSummaries = cpksMetadata.map { it.toCpkSummary() }
        val verificationSandboxService = virtualNodeService.verificationSandboxService
        val sandbox = verificationSandboxService.get(holdingIdentity, cpkSummaries)
        val transaction = createTestTransaction(sandbox, isValid = false)
        val request = createRequest(sandbox, holdingIdentity, transaction, cpkSummaries)

        // Create request processor
        val processor = VerificationRequestProcessor(
            currentSandboxGroupContext,
            verificationSandboxService,
            VerificationRequestHandlerImpl(externalEventResponseFactory),
            externalEventResponseFactory,
            TransactionVerificationRequest::class.java,
            FlowEvent::class.java
        )

        // Send request to message processor
        val flowEvent = processor.process(request)

        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNull()
        assertThat(response.requestId).isEqualTo(REQUEST_ID)

        val transactionVerificationResponse = deserializer.deserialize(response.payload.array())!!
        assertThat(transactionVerificationResponse.verificationStatus).isEqualTo(TransactionVerificationStatus.INVALID)
        assertThat(transactionVerificationResponse.verificationFailure).isNotNull

        val failure = transactionVerificationResponse.verificationFailure
        assertThat(failure.errorType).isEqualTo(ContractVerificationException::class.java.canonicalName)
        assertThat(failure.errorMessage).contains(VERIFICATION_ERROR_MESSAGE)
    }

    @Test
    fun `returns error after when CPK not available`() {
        val virtualNodeInfo = virtualNodeService.load(TEST_CPB)
        val holdingIdentity = virtualNodeInfo.holdingIdentity
        val cpksMetadata =
            cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier)!!.cpksMetadata.filter { it.isContractCpk() }
        val cpkSummaries = cpksMetadata.map { it.toCpkSummary() }
        val verificationSandboxService = virtualNodeService.verificationSandboxService
        val sandbox = verificationSandboxService.get(holdingIdentity, cpkSummaries)
        val transaction = createTestTransaction(sandbox, isValid = true)
        val request = createRequest(sandbox, holdingIdentity, transaction, listOf(NON_EXISTING_CPK))

        val processor = VerificationRequestProcessor(
            currentSandboxGroupContext,
            verificationSandboxService,
            VerificationRequestHandlerImpl(externalEventResponseFactory),
            externalEventResponseFactory,
            TransactionVerificationRequest::class.java,
            FlowEvent::class.java
        )

        // Send request to message processor (there were max number of redeliveries)
        val flowEvent = processor.process(request)

        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        val error = response.error
        assertThat(error.errorType).isEqualTo(ExternalEventResponseErrorType.TRANSIENT)
        assertThat(error.exception.errorType).isEqualTo(CpkNotAvailableException::class.java.canonicalName)
        assertThat(response.requestId).isEqualTo(REQUEST_ID)
    }

    private fun createTestTransaction(ctx: SandboxGroupContext, isValid: Boolean): UtxoLedgerTransactionContainer {
        val signatory = ctx.getSerializationService().serialize(publicKeyExample).bytes

        val input = ctx.getSerializationService().serialize(
            StateRef(parseSecureHash("SHA-256:1111111111111111"), 0)
        ).bytes

        val outputState = ctx.getSerializationService().serialize(
            TestState(isValid, listOf())
        ).bytes

        val outputInfo = ctx.getSerializationService().serialize(
            UtxoOutputInfoComponent(
                null, null, NOTARY_X500_NAME, PUBLIC_KEY, outputState::class.java.canonicalName, "contract tag"
            )
        ).bytes

        val command = ctx.getSerializationService().serialize(TestCommand()).bytes

        val wireTransaction = wireTransactionFactory.createExample(
            jsonMarshallingService,
            jsonValidator,
            listOf(
                emptyList(),
                listOf(signatory),
                listOf(outputInfo),
                emptyList(),
                emptyList(),
                listOf(input),
                emptyList(),
                listOf(outputState),
                listOf(command)
            ),
            ledgerModel = UtxoLedgerTransactionImpl::class.java.name,
            transactionSubType = "GENERAL",
            memberShipGroupParametersHash = "MEMBERSHIP_GROUP_PARAMETERS_HASH"
        )
        val inputStateAndRefs: List<StateAndRef<*>> = listOf()
        val referenceStateAndRefs: List<StateAndRef<*>> = listOf()

        val groupParameters = KeyValuePairList(
            listOf(
                KeyValuePair("corda.epoch", "5"),
                KeyValuePair("corda.modifiedTime", Instant.now().toString()),
            ).sorted()
        )
        val serializedGroupParameters = keyValueSerializer.serialize(groupParameters)!!
        val mgmSignatureGroupParameters = DigitalSignatureWithKey(publicKeyExample, "bytes".toByteArray())

        val signedGroupParameters = groupParametersFactory.create(
            ByteBuffer.wrap(serializedGroupParameters).array(),
            mgmSignatureGroupParameters,
            SignatureSpecImpl("dummySignatureName")
        )

        return UtxoLedgerTransactionContainer(wireTransaction, inputStateAndRefs, referenceStateAndRefs, signedGroupParameters)
    }

    private fun CpkMetadata.toCpkSummary() =
        CordaPackageSummary(
            cpkId.name,
            cpkId.version,
            cpkId.signerSummaryHash.toString(),
            fileChecksum.toString()
        )

    private fun createRequest(
        ctx: SandboxGroupContext,
        holdingIdentity: HoldingIdentity,
        transaction: UtxoLedgerTransactionContainer,
        cpksSummaries: List<CordaPackageSummary>
    ) = TransactionVerificationRequest(
        Instant.now(),
        holdingIdentity.toAvro(),
        ctx.serialize(transaction),
        cpksSummaries,
        EXTERNAL_EVENT_CONTEXT
    )

    private fun SandboxGroupContext.serialize(obj: Any) =
        ByteBuffer.wrap(getSerializationService().serialize(obj).bytes)

    private fun SandboxGroupContext.getSerializationService(): SerializationService =
        getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
            ?: throw CordaRuntimeException(
                "Entity serialization service not found within the sandbox for identity: " +
                        "${virtualNodeContext.holdingIdentity}"
            )

    @BelongsToContract(TestContract::class)
    class TestState(val valid: Boolean, private val participants: List<PublicKey>) : ContractState {
        override fun getParticipants(): List<PublicKey> {
            return participants
        }
    }

    class TestContract : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {
            require(transaction.inputStateRefs.isNotEmpty()) {
                "At least one input expected"
            }
            require(transaction.outputStateAndRefs.isNotEmpty()) {
                "At least one output expected"
            }
            val state = transaction.outputStateAndRefs.first().state.contractState as TestState
            require(state.valid) {
                VERIFICATION_ERROR_MESSAGE
            }
        }
    }

    class TestCommand : Command
}