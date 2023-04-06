package net.corda.ledger.persistence.consensual.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.db.testkit.DbUtils
import net.corda.flow.utils.keyValuePairListOf
import net.corda.flow.utils.toKeyValuePairList
import net.corda.flow.utils.toMap
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.persistence.processor.DelegatedRequestHandlerSelector
import net.corda.ledger.persistence.processor.PersistenceRequestProcessor
import net.corda.ledger.persistence.utxo.tests.UtxoLedgerMessageProcessorTests
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
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
import java.time.Instant
import java.util.UUID

/**
 * To use Postgres rather than in-memory (HSQL):
 *
 *     docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
 *
 *     gradlew integrationTest -PpostgresPort=5432
 *
 * Rather than creating a new serializer in these tests from scratch,
 * we grab a reference to the one in the sandbox and use that to serialize and de-serialize.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class ConsensualLedgerMessageProcessorTests {
    companion object {
        const val TOPIC = "consensual-ledger-dummy-topic"
        const val TIMEOUT_MILLIS = 10000L
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext(
            "request id", "flow id", KeyValuePairList(listOf(KeyValuePair("corda.account", "test account")))
        )
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
    fun `persistTransaction for consensual ledger deserialises the transaction and persists`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)
        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(virtualNodeInfo)
        val ctx = virtualNode.entitySandboxService.get(virtualNodeInfo.holdingIdentity, cpkFileHashes)

        logger.warn("BM TEST - CPK file hashes from CPI info read: $cpkFileHashes")

        val transaction = createTestTransaction(ctx)

        // Serialise tx into bytebuffer and add to PersistTransaction payload
        val serializedTransaction = ctx.serialize(transaction)
        val transactionStatus = TransactionStatus.VERIFIED.value
        val persistTransaction = PersistTransaction(serializedTransaction, transactionStatus, emptyList())
        val request = createRequest(
            virtualNodeInfo.holdingIdentity,
            persistTransaction,
            UtxoLedgerMessageProcessorTests.EXTERNAL_EVENT_CONTEXT.apply {
                this.contextProperties = keyValuePairListOf(
                    this.contextProperties.toMap() +
                    cpkFileHashes.toKeyValuePairList(CPK_FILE_CHECKSUM).toMap()
                )
            }
        )

        // Send request to message processor
        val processor = PersistenceRequestProcessor(
            virtualNode.entitySandboxService,
            delegatedRequestHandlerSelector,
            responseFactory
        )

        val requestId = UUID.randomUUID().toString()
        val records = listOf(Record(TOPIC, requestId, request))

        // Process the messages (this should persist transaction to the DB)
        var responses = assertSuccessResponses(processor.onNext(records))
        assertThat(responses).hasSize(1)

        // Check that we wrote the expected things to the DB
        val findRequest = createRequest(
            virtualNodeInfo.holdingIdentity,
            FindTransaction(transaction.id.toString(), TransactionStatus.VERIFIED.value)
        )
        responses = assertSuccessResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), findRequest))))

        assertThat(responses).hasSize(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNull()
        val entityResponse = deserializer.deserialize(response.payload.array())!!
        assertThat(entityResponse.results).hasSize(1)
        assertThat(entityResponse.results.first()).isEqualTo(serializedTransaction)
        val retrievedTransaction = ctx.deserialize<SignedTransactionContainer>(entityResponse.results.first())
        assertThat(retrievedTransaction).isEqualTo(transaction)
    }

    private fun createTestTransaction(ctx: SandboxGroupContext): SignedTransactionContainer {
        val wireTransactionFactory: WireTransactionFactory = ctx.getSandboxSingletonService()
        val wireTransaction = wireTransactionFactory.createExample(
            ctx.getSandboxSingletonService(),
            ctx.getSandboxSingletonService()
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
        logger.debug { "Consensual ledger persistence request: ${request.javaClass.simpleName} $request" }
        return LedgerPersistenceRequest(
            Instant.now(),
            holdingId.toAvro(),
            LedgerTypes.CONSENSUAL,
            request,
            externalEventContext
        )
    }

    private fun assertSuccessResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as ExternalEventResponse
            if (response.error != null) {
                logger.error("Incorrect error response: {}", response.error)
            }
            assertThat(response.error).isNull()
        }
        return records
    }

    /* Simple wrapper to serialize bytes correctly during test */
    private fun SandboxGroupContext.serialize(obj: Any) =
        ByteBuffer.wrap(getSerializationService().serialize(obj).bytes)

    /* Simple wrapper to deserialize */
    private inline fun <reified T : Any> SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializationService().deserialize<T>(bytes.array())
}
