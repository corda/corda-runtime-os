package net.corda.ledger.persistence.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.*
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.db.schema.DbSchema
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.ledger.persistence.impl.tests.helpers.DbTestContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.ledger.consensual.FindTransaction
import net.corda.db.persistence.testkit.helpers.SandboxHelper.getSerializer
import net.corda.db.testkit.DbUtils
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.transaction.serialization.internal.WireTransactionSerializer
import net.corda.ledger.persistence.impl.internal.ConsensualLedgerMessageProcessor
import net.corda.persistence.common.EntitySandboxContextTypes.SANDBOX_SERIALIZER
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.osgi.framework.BundleContext
import org.osgi.service.component.ComponentContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
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
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsensualLedgerMessageProcessorTests {
    companion object {
        const val INTERNAL_CUSTOM_SERIALIZERS = "internalCustomSerializers"
        const val TOPIC = "consensual-ledger-dummy-topic"
        val EXTERNAL_EVENT_CONTEXT = ExternalEventContext("request id", "flow id", KeyValuePairList(emptyList()))
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
    private lateinit var deserializer: CordaAvroDeserializer<EntityResponse>

    @InjectService
    lateinit var digestService: DigestService
    @InjectService
    lateinit var merkleTreeFactory: MerkleTreeFactory
    lateinit var wireTransactionSerializer: WireTransactionSerializer
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
            wireTransactionSerializer = WireTransactionSerializer(merkleTreeFactory, digestService)
            deserializer = setup.fetchService<CordaAvroSerializationFactory>(timeout = 10000)
                .createAvroDeserializer({}, EntityResponse::class.java)
        }
    }

    @BeforeEach
    fun beforeEach() {
        ctx = createDbTestContext()
        // Each test is likely to leave junk lying around in the tables before the next test.
        // We can't trust deleting the tables because tests can run concurrently.
    }

    /* Simple wrapper to serialize bytes correctly during test */
    private fun SandboxGroupContext.serialize(obj: Any): ByteBuffer {
        val serializer = getSerializer(SANDBOX_SERIALIZER)
        //val serializer = serializationService
        return ByteBuffer.wrap(serializer.serialize(obj).bytes)
    }

    /* Simple wrapper to serialize bytes correctly during test */
    private fun DbTestContext.serialize(obj: Any) = sandbox.serialize(obj)

    /* Simple wrapper to deserialize */
    private fun SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializer(SANDBOX_SERIALIZER).deserialize(bytes.array(), Any::class.java)

    /* Simple wrapper to deserialize */
    private fun DbTestContext.deserialize(bytes: ByteBuffer) = sandbox.deserialize(bytes)

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    @Test
    fun `persistTransaction for consensual ledger deserialises the tx and persists`() {
        // Native SQL is used that is specific to Postgres and won't work with in-memory DB
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        // TODO Refactor this after https://github.com/corda/corda-runtime-os/pull/2201 is merged
        //  see https://github.com/corda/corda-runtime-os/pull/1883#discussion_r979778303

        // create ConsensualSignedTransactionImpl instance (or WireTransaction at first)
        val mapper = jacksonObjectMapper()

        val transactionMetaData = TransactionMetaData(
            mapOf(
                TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
            )
        )
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())
        val componentGroupLists = listOf(
            listOf(mapper.writeValueAsBytes(transactionMetaData)),
            listOf(".".toByteArray()),
            listOf("abc d efg".toByteArray()),
        )
        val tx = WireTransaction(merkleTreeFactory, digestService, privacySalt, componentGroupLists)
        logger.info("WireTransaction: ", tx)

        // serialise tx into bytebuffer and add to PersistTransaction payload
        val serializedTransaction = ctx.serialize(tx)
        val persistTransaction = PersistTransaction(serializedTransaction)
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, persistTransaction)

        // send request to message processor
        val processor = ConsensualLedgerMessageProcessor(
            ctx.entitySandboxService,
            externalEventResponseFactory,
            merkleTreeFactory,
            digestService,
            this::noOpPayloadCheck)
        val requestId = UUID.randomUUID().toString()
        val records = listOf(Record(TOPIC, requestId, request))

        // process the messages. This should result in ConsensualStateDAO persisting things to the DB
        var responses = assertSuccessResponses(processor.onNext(records))
        assertThat(responses.size).isEqualTo(1)

        // check that we wrote the expected things to the DB
        val findRequest = createRequest(ctx.virtualNodeInfo.holdingIdentity, FindTransaction(tx.id.toHexString()))
        responses = assertSuccessResponses(processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), findRequest))))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNull()
        val entityResponse = deserializer.deserialize(response.payload.array())!!
        assertThat(entityResponse.results.size).isEqualTo(1)
        assertThat(entityResponse.results.first()).isEqualTo(serializedTransaction) // need to reconstruct tx from the serialised response
    }

    private fun createDbTestContext(): DbTestContext {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val testId = (0..1000000).random() // keeping this shorter than UUID.
        val schemaName = "consensual_ledger_test_$testId"
        val animalDbConnection = Pair(virtualNodeInfo.vaultDmlConnectionId, "animals-node-$testId")
        val dbConnectionManager = FakeDbConnectionManager(listOf(animalDbConnection), schemaName)

        val componentContext = Mockito.mock(ComponentContext::class.java)
        whenever(componentContext.locateServices(INTERNAL_CUSTOM_SERIALIZERS))
            .thenReturn(arrayOf(wireTransactionSerializer))

        // set up sandbox
        val entitySandboxService =
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                dbConnectionManager,
                componentContext
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
