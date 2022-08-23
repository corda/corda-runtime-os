package net.corda.processors.ledger.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.*
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.schema.DbSchema
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.entityprocessor.impl.tests.fake.FakeDbConnectionManager
import net.corda.entityprocessor.impl.tests.helpers.BasicMocks
import net.corda.entityprocessor.impl.tests.helpers.DbTestContext
import net.corda.entityprocessor.impl.tests.helpers.Resources
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getCatClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getDogClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getOwnerClass
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getSerializer
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.processors.ledger.impl.ConsensualLedgerProcessor
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
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
        const val TOPIC = "pretend-topic"
        private val logger = contextLogger()
    }

    @InjectService
    lateinit var lbm: LiquibaseSchemaMigrator

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

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
        logger.info("Setup test (test Directory: $testDirectory)")
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(timeout = 10000)
            cpiInfoReadService = setup.fetchService(timeout = 10000)
            virtualNodeInfoReadService = setup.fetchService(timeout = 10000)
        }
    }

    @BeforeEach
    fun beforeEach() {
        ctx = createDbTestContext()
        // Each test is likely to leave junk lying around in the tables before the next test.
        // We can't trust deleting the tables because tests can run concurrently.
    }

    /** Simple wrapper to serialize bytes correctly during test */
    private fun SandboxGroupContext.serialize(obj: Any) = ByteBuffer.wrap(getSerializer().serialize(obj).bytes)

    /** Simple wrapper to serialize bytes correctly during test */
    private fun DbTestContext.serialize(obj: Any) = sandbox.serialize(obj)

    /** Simple wrapper to deserialize */
    private fun SandboxGroupContext.deserialize(bytes: ByteBuffer) =
        getSerializer().deserialize(bytes.array(), Any::class.java)

    /** Simple wrapper to deserialize */
    private fun DbTestContext.deserialize(bytes: ByteBuffer) = sandbox.deserialize(bytes)

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    @Test
    fun `persistTransaction for consensual ledger actually persists`() {
        val payload = ByteBuffer.allocate(1)
        logger.info("Creating request")
        val request = createRequest(ctx.virtualNodeInfo.holdingIdentity, PersistTransaction(payload))
        logger.info("request: ${request}")

        // send request to message processor
        logger.info("Creating entity message processor")
        val processor = ConsensualLedgerProcessor(ctx.entitySandboxService, UTCClock(), this::noOpPayloadCheck)
        val requestId = UUID.randomUUID().toString()
        val records = listOf(Record(TOPIC, requestId, request))

        // Process the messages. This should result in ConsensualStateDAO persisting things to the DB
        logger.info("Sending request to processor")
        val responses = assertSuccessResponses(processor.onNext(records))

        // assert persisted
        assertThat(responses.size).isEqualTo(1)

        // TODO: check what happened in the DB. What exactly are we looking for?
        /*
        // check the db directly (rather than using our code)
        val findDog = ctx.findDog(dogId)

        // It's the dog we persisted.
        assertThat(findDog).isEqualTo(dog)
        logger.info("Woof $findDog")
         */
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

        // custom schema...
        val dogClass = sandbox.sandboxGroup.getDogClass()
        val catClass = sandbox.sandboxGroup.getCatClass()
        val customSchema = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    dogClass.packageName, listOf("migration/db.changelog-master.xml"),
                    classLoader = dogClass.classLoader
                ),
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    catClass.packageName, listOf("migration/db.changelog-master.xml"),
                    classLoader = catClass.classLoader
                )
            )
        )

        lbm.updateDb(dbConnectionManager.getDataSource(animalDbConnection.first).connection, vaultSchema)
        lbm.updateDb(dbConnectionManager.getDataSource(animalDbConnection.first).connection, customSchema)

        return DbTestContext(
            virtualNodeInfo,
            entitySandboxService,
            sandbox,
            dbConnectionManager.createEntityManagerFactory(
                animalDbConnection.first,
                JpaEntitiesSet.create(
                    animalDbConnection.second,
                    setOf(dogClass, catClass, sandbox.sandboxGroup.getOwnerClass())
                )
            ),
            dogClass, catClass,
            schemaName
        )
    }

    private fun assertSuccessResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as EntityResponse
            if (response.responseType is EntityResponseFailure) {
                logger.error("$response.responseType")
            }
            assertThat(response.responseType).isInstanceOf(EntityResponseSuccess::class.java)
        }
        return records
    }

    private fun assertFailureResponses(records: List<Record<*, *>>): List<Record<*, *>> {
        records.forEach {
            val flowEvent = it.value as FlowEvent
            val response = flowEvent.payload as EntityResponse
            if (response.responseType is EntityResponseSuccess) {
                logger.error("$response.responseType")
            }
            assertThat(response.responseType).isInstanceOf(EntityResponseFailure::class.java)
        }
        return records
    }

    private fun assertThatResponseIsAList(entityResponse: EntityResponse): List<*> {
        val entityResponseSuccess = entityResponse.responseType as EntityResponseSuccess
        val bytes = entityResponseSuccess.result as ByteBuffer
        val results = ctx.deserialize(bytes)

        // We have a list
        assertThat(results as List<*>).isInstanceOf(List::class.java)

        return results
    }

    private fun createRequest(holdingId: net.corda.virtualnode.HoldingIdentity, entity: Any): ConsensualLedgerRequest {
        logger.info("Entity Request - entity: ${entity.javaClass.simpleName} $entity")
        return ConsensualLedgerRequest(Instant.now(), UUID.randomUUID().toString(), holdingId.toAvro(), entity)
    }
}
