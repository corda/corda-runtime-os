package net.corda.ledger.persistence.utxo.tests

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.flow.utils.keyValuePairListOf
import net.corda.flow.utils.toKeyValuePairList
import net.corda.flow.utils.toMap
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.persistence.assertSuccessResponse
import net.corda.ledger.persistence.consensual.tests.ConsensualLedgerMessageProcessorTests
import net.corda.ledger.persistence.processor.DelegatedRequestHandlerSelector
import net.corda.ledger.persistence.processor.LedgerPersistenceRequestProcessor
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.ContractState
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
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant

/**
 * To use Postgres rather than in-memory (HSQL):
 *
 *     docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
 *
 *     gradlew integrationTest -PdatabaseType=POSTGRES
 *
 *     See libs/db/readme.md for a full list of database options.
 *
 * Rather than creating a new serializer in these tests from scratch,
 * we grab a reference to the one in the sandbox and use that to serialize and de-serialize.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class UtxoLedgerMessageProcessorTests {
    companion object {
        const val TIMEOUT_MILLIS = 10000L
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext(
            "request id", "flow id", KeyValuePairList(listOf(KeyValuePair("corda.account", "test account")))
        )
        private val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
        private val publicKeyExample: PublicKey = KeyPairGenerator.getInstance("RSA")
            .also { it.initialize(512) }.genKeyPair().public
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    // For sandboxing
    private lateinit var virtualNode: VirtualNodeService
    private lateinit var responseFactory: ResponseFactory
    private lateinit var deserializer: CordaAvroDeserializer<EntityResponse>
    private lateinit var delegatedRequestHandlerSelector: DelegatedRequestHandlerSelector
    private lateinit var cpiInfoReadService: CpiInfoReadService

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var currentSandboxGroupContext: CurrentSandboxGroupContext

    private val requestClass = LedgerPersistenceRequest::class.java
    private val responseClass = FlowEvent::class.java

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        logger.info("Setup test (test directory: {})", testDirectory)
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            responseFactory = setup.fetchService(TIMEOUT_MILLIS)
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
            deserializer = setup.fetchService<CordaAvroSerializationFactory>(TIMEOUT_MILLIS)
                .createAvroDeserializer({}, EntityResponse::class.java)
            delegatedRequestHandlerSelector = setup.fetchService(TIMEOUT_MILLIS)
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    @Test
    fun `persistTransaction for utxo ledger deserialises the transaction and persists`() {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)
        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(virtualNodeInfo)
        val ctx = virtualNode.entitySandboxService.get(virtualNodeInfo.holdingIdentity, cpkFileHashes)
        val transaction = createTestTransaction(ctx)

        // Serialise tx into bytebuffer and add to PersistTransaction payload
        val serializedTransaction = ctx.serialize(transaction)
        val transactionStatus = TransactionStatus.VERIFIED.value
        val visibleStatesIndexes = listOf(0)
        val persistTransaction = PersistTransaction(serializedTransaction, transactionStatus, visibleStatesIndexes)
        val request = createRequest(
            virtualNodeInfo.holdingIdentity,
            persistTransaction,
            ConsensualLedgerMessageProcessorTests.EXTERNAL_EVENT_CONTEXT.apply {
                this.contextProperties = keyValuePairListOf(
                    this.contextProperties.toMap() +
                            cpkFileHashes.toKeyValuePairList(CPK_FILE_CHECKSUM).toMap()
                )
            })

        // Send request to message processor
        val processor = LedgerPersistenceRequestProcessor(
            currentSandboxGroupContext,
            virtualNode.entitySandboxService,
            delegatedRequestHandlerSelector,
            responseFactory,
            requestClass,
            responseClass
        )

        // Process the messages (this should persist transaction to the DB)
        assertSuccessResponse(processor.process(request), logger)

        // Check that we wrote the expected things to the DB
        val findRequest = createRequest(
                virtualNodeInfo.holdingIdentity,
                FindTransaction(transaction.id.toString(), TransactionStatus.VERIFIED.value),
                EXTERNAL_EVENT_CONTEXT.apply {
                this.contextProperties = keyValuePairListOf(
                    this.contextProperties.toMap() +
                            cpkFileHashes.toKeyValuePairList(CPK_FILE_CHECKSUM).toMap()
                )
            })

        val result = assertSuccessResponse(processor.process(findRequest), logger)
        val entityResponse = deserializer.deserialize(result.payload.array())!!
        assertThat(entityResponse.results).hasSize(1)
        val retrievedTransaction = ctx.deserialize<Pair<SignedTransactionContainer, String>>(entityResponse.results.first())
        assertThat(retrievedTransaction).isEqualTo(transaction to "V")
    }

    private fun createTestTransaction(ctx: SandboxGroupContext): SignedTransactionContainer {
        val outputState = ctx.getSerializationService().serialize(
            TestContractState()
        ).bytes
        val outputInfo = ctx.getSerializationService().serialize(
            UtxoOutputInfoComponent(
                null, null, notaryX500Name, publicKeyExample, TestContractState::class.java.name, "contract tag"
            )
        ).bytes
        val wireTransactionFactory: WireTransactionFactory = ctx.getSandboxSingletonService()
        val wireTransaction = wireTransactionFactory.createExample(
            ctx.getSandboxSingletonService(),
            ctx.getSandboxSingletonService(),
            listOf(
                listOf("1".toByteArray()),
                listOf("2".toByteArray()),
                listOf(outputInfo),
                listOf("4".toByteArray()),
                listOf("5".toByteArray()),
                emptyList(),
                emptyList(),
                listOf(outputState),
                listOf("9".toByteArray())
            ),
            ledgerModel = UtxoLedgerTransactionImpl::class.java.name,
            transactionSubType = "GENERAL",
            memberShipGroupParametersHash = "MEMBERSHIP_GROUP_PARAMETERS_HASH"
        )
        return SignedTransactionContainer(
            wireTransaction,
            listOf(getSignatureWithMetadataExample())
        )
    }

    private fun createRequest(
        holdingId: net.corda.virtualnode.HoldingIdentity,
        request: Any,
        externalEventContext: ExternalEventContext = EXTERNAL_EVENT_CONTEXT
    ): LedgerPersistenceRequest {
        logger.debug { "UTXO ledger persistence request: ${request.javaClass.simpleName} $request" }
        return LedgerPersistenceRequest(
            Instant.now(),
            holdingId.toAvro(),
            LedgerTypes.UTXO,
            request,
            externalEventContext
        )
    }

    class TestContractState : ContractState {

        override fun getParticipants(): List<PublicKey> {
            return emptyList()
        }
    }

    /* Simple wrapper to serialize bytes correctly during test */
    private fun SandboxGroupContext.serialize(obj: Any) =
        ByteBuffer.wrap(getSerializationService().serialize(obj).bytes)

    /* Simple wrapper to deserialize */
    private inline fun <reified T : Any> SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializationService().deserialize<T>(bytes.array())
}
