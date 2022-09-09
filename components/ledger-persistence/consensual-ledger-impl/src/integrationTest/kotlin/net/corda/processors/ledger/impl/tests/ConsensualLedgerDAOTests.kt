package net.corda.processors.ledger.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.*
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.schema.DbSchema
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.entityprocessor.impl.tests.fake.FakeDbConnectionManager
import net.corda.entityprocessor.impl.tests.helpers.BasicMocks
import net.corda.entityprocessor.impl.tests.helpers.Resources
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getSerializer
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.processors.ledger.impl.ConsensualLedgerProcessor
import net.corda.processors.ledger.impl.tests.helpers.DbTestContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.processors.ledger.impl.MappablePrivacySalt
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

// TODO: Move common parts outside rather than copy-pasting from PersistenceServiceInternalTests.kt

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
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsensualLedgerDAOTests {
    companion object {
        const val TOPIC = "consensual-ledger-dummy-topic"
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext("request id", "flow id")
        private val logger = contextLogger()
    }

    @InjectService
    lateinit var lbm: LiquibaseSchemaMigrator

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    // For sandboxing
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNode: VirtualNodeService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var externalEventResponseFactory: ExternalEventResponseFactory

    @InjectService
    lateinit var digestService: DigestService
    @InjectService
    lateinit var merkleTreeFactory: MerkleTreeFactory

    private lateinit var ctx: DbTestContext

    @BeforeAll
    fun setup(
        @InjectService(timeout = 5000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        logger.info("Setup test (test directory: $testDirectory)")
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            externalEventResponseFactory = setup.fetchService(timeout = 10000)
            cpiInfoReadService = setup.fetchService(timeout = 10000)
            virtualNode = setup.fetchService(timeout = 10000)
            virtualNodeInfoReadService = setup.fetchService(timeout = 10000)
        }

        /* TODO: This doesn't work yet because SerializationService is not injectable. There is an open PR that adds
            an injectable SerializationService though.
         */
        /*
        serializationService = TestSerializationService.getTestSerializationService({
            it.register(WireTransactionSerializer(merkleTreeFactory, digestService), it)
        }, schemeMetadata)
         */
    }

    @BeforeEach
    fun beforeEach() {
        ctx = createDbTestContext()
        // Each test is likely to leave junk lying around in the tables before the next test.
        // We can't trust deleting the tables because tests can run concurrently.
    }

    /* Simple wrapper to serialize bytes correctly during test */
    private fun SandboxGroupContext.serialize(obj: Any) = ByteBuffer.wrap(getSerializer().serialize(obj).bytes)

    /* Simple wrapper to serialize bytes correctly during test */
    private fun DbTestContext.serialize(obj: Any) = sandbox.serialize(obj)

    /* Simple wrapper to deserialize */
    private fun SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializer().deserialize(bytes.array(), Any::class.java)

    /* Simple wrapper to deserialize */
    private fun DbTestContext.deserialize(bytes: ByteBuffer) = sandbox.deserialize(bytes)

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    @Test
    fun `persistTransaction for consensual ledger deserialises the tx and persists`() {
        // create ConsensualSignedTransactionImpl instance (or WireTransaction at first)
        val mapper = jacksonObjectMapper()
        mapper.addMixIn(PrivacySalt::class.java, MappablePrivacySalt::class.java)

        val transactionMetaData = TransactionMetaData(
            mapOf(
                TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
            )
        )
        val privacySalt = MappablePrivacySalt("1".repeat(32).toByteArray())
        val componentGroupLists = listOf(
            listOf(mapper.writeValueAsBytes(transactionMetaData)),
            listOf(".".toByteArray()),
            listOf("abc d efg".toByteArray()),
        )
        val wireTransaction = WireTransaction(merkleTreeFactory, digestService, privacySalt, componentGroupLists)
        logger.info("WireTransaction: ", wireTransaction)

        // serialise tx into bytebuffer and add to PersistTransaction payload
        // This won't work because WireTransaction isn't marked with @CordaSerializable
        // val txBytes = ctx.serialize(wireTransaction)
        val txBytes = mapper.writeValueAsBytes(wireTransaction)
        logger.info(txBytes.toString(Charset.defaultCharset()))

        // create request
//        val payload = ByteBuffer.allocate(1)
        val payload = ByteBuffer.wrap(txBytes)
        logger.info("Creating request")
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, PersistTransaction(payload))
        logger.info("request: $request")

        // send request to message processor
        logger.info("Creating message processor")
        val processor = ConsensualLedgerProcessor(
            ctx.entitySandboxService,
            externalEventResponseFactory,
            this::noOpPayloadCheck)
        val requestId = UUID.randomUUID().toString()
        val records = listOf(Record(TOPIC, requestId, request))

        // process the messages. This should result in ConsensualStateDAO persisting things to the DB
        logger.info("Sending request to processor")
        val responses = assertSuccessResponses(processor.onNext(records))
        assertThat(responses.size).isEqualTo(1)

        // check that we wrote the expected things to the DB
        // val retrievedTx = ctx.findTxById(1)
        // assertThat(retrievedTx.id.isEqualTo(1))
    }

    private fun createDbTestContext(): DbTestContext {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val testId = (0..1000000).random() // keeping this shorter than UUID.
        val schemaName = "consensual_ledger_test_$testId"
        val animalDbConnection = Pair(virtualNodeInfo.vaultDmlConnectionId, "animals-node-$testId")
        val dbConnectionManager = FakeDbConnectionManager(listOf(animalDbConnection), schemaName)

        // set up sandbox
        val entitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                BasicMocks.componentContext()
            )

        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        // migrate DB schema
        val vaultSchema = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/vnode-vault/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )

        lbm.updateDb(dbConnectionManager.getDataSource(animalDbConnection.first).connection, vaultSchema)

        return DbTestContext(
            virtualNodeInfo,
            entitySandboxService,
            sandbox,
            dbConnectionManager.createEntityManagerFactory(
                animalDbConnection.first,
                JpaEntitiesSet.create(
                    animalDbConnection.second,
                    setOf()
                )
            ),
            schemaName
        )
    }

    private fun assertSuccessResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as ExternalEventResponse
            if (response.error != null) {
                logger.error("Incorrect error response: ${response.error}")
            }
            assertThat(response.error).isNull()
        }
        return records
    }

    private fun assertFailureResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as ExternalEventResponse
            if (response.error == null) {
                logger.error("Incorrect successful response: ${response.error}")
            }
            assertThat(response.error).isNotNull()
        }
        return records
    }

    private fun createRequest(holdingId: net.corda.virtualnode.HoldingIdentity,
                              request: Any,
                              externalEventContext: ExternalEventContext = EXTERNAL_EVENT_CONTEXT
    ): ConsensualLedgerRequest {
        logger.info("Consensual ledger persistence request: ${request.javaClass.simpleName} $request")
        return ConsensualLedgerRequest(Instant.now(), holdingId.toAvro(), request, externalEventContext)
    }
}
