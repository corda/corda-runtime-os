package net.corda.applications.workers.smoketest.services

import net.corda.applications.workers.smoketest.utils.PLATFORM_VERSION
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.common.json.validation.JsonValidator
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.parseSecureHash
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.utxo.StateRef
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.utxo.verification.TransactionVerificationResponse
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.Contract
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Tests for the Verification RPC service
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerificationRPCSmokeTests {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val serializationFactory = CordaAvroSerializationFactoryImpl(
        AvroSchemaRegistryImpl()
    )

    private val avroSerializer = serializationFactory.createAvroSerializer<TransactionVerificationRequest> { }
    private val avroFlowEventDeserializer = serializationFactory.createAvroDeserializer({}, FlowEvent::class.java)
    private val avroVerificationDeserializer =
        serializationFactory.createAvroDeserializer({}, TransactionVerificationResponse::class.java)

    companion object {
        const val TEST_CPI_NAME = "ledger-utxo-demo-app"
        const val TEST_CPB_LOCATION = "/META-INF/ledger-utxo-demo-app.cpb"
        const val NOTARY_SERVICE_X500 = "O=MyNotaryService, L=London, C=GB"
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val TOPIC = "ledger-verification-dummy-topic"
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

    private val testRunUniqueId = UUID.randomUUID()

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
    fun `RPC endpoint accepts a request and returns back a response`() {
        val virtualNodeInfo = virtualNodeService.load(TEST_CPB)
        val holdingIdentity = virtualNodeInfo.holdingIdentity
        val cpksMetadata =
            cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier)!!.cpksMetadata.filter { it.isContractCpk() }
        val cpkSummaries = cpksMetadata.map { it.toCpkSummary() }
        val verificationSandboxService = virtualNodeService.verificationSandboxService
        val sandbox = verificationSandboxService.get(holdingIdentity, cpkSummaries)
        val transaction = createTestTransaction(sandbox, isValid = true)
        val request = createRequest(sandbox, holdingIdentity, transaction, cpkSummaries)

        val url = "${System.getProperty("verificationWorkerUrl")}api/$PLATFORM_VERSION/verification"

        logger.info("verification url: $url")
        val serializedPayload = avroSerializer.serialize(payloadBuilder().build())

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        assertThat(response.statusCode()).isEqualTo(200)
            .withFailMessage("status code on response: ${response.statusCode()} url: $url")

        val responseBody: ByteArray = response.body()
        val responseEvent = avroFlowEventDeserializer.deserialize(responseBody)

        assertThat(responseEvent).isNotNull

        val deserializedExternalEventResponse =
            avroVerificationDeserializer.deserialize((responseEvent?.payload as ExternalEventResponse).payload.array())

        assertThat(deserializedExternalEventResponse).isNotNull
        //TODO - need a verification assertion
    }

    private fun SandboxGroupContext.getSerializationService(): SerializationService =
        getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
            ?: throw CordaRuntimeException(
                "Entity serialization service not found within the sandbox for identity: " +
                        "${virtualNodeContext.holdingIdentity}"
            )

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

        return UtxoLedgerTransactionContainer(
            wireTransaction,
            inputStateAndRefs,
            referenceStateAndRefs,
            signedGroupParameters
        )
    }

    private fun CpkMetadata.toCpkSummary() =
        CordaPackageSummary(
            cpkId.name,
            cpkId.version,
            cpkId.signerSummaryHash.toString(),
            fileChecksum.toString()
        )

    //TODO - need verification payload
    private fun payloadBuilder(
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