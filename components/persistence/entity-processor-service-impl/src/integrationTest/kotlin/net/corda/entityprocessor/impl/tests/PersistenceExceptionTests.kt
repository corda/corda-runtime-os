package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpk.read.CpkReadService
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.MergeEntities
import net.corda.data.persistence.PersistEntities
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.db.persistence.testkit.helpers.SandboxHelper.createDog
import net.corda.db.persistence.testkit.helpers.SandboxHelper.createVersionedDog
import net.corda.db.persistence.testkit.helpers.SandboxHelper.getDogClass
import net.corda.db.persistence.testkit.helpers.SandboxHelper.getVersionedDogClass
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.flow.utils.toKeyValuePairList
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.UUID


/**
 * Test persistence exceptions.  Most of these tests don't require the database
 * set up parts, because we're mocking.
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceExceptionTests {
    companion object {
        const val TOPIC = "pretend-topic"
        private const val TIMEOUT_MILLIS = 10000L
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val DOGS_TABLE = "migration/db.changelog-master.xml"
        const val DOGS_TABLE_WITHOUT_PK = "dogs-without-pk.xml"
        const val VERSIONED_DOGS_TABLE = "versioned-dogs.xml"
    }

    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    private val sandboxLifecycle = AllTestsLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var cpkReadService: CpkReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var responseFactory: ResponseFactory

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var currentSandboxGroupContext: CurrentSandboxGroupContext

    private lateinit var dbConnectionManager: FakeDbConnectionManager
    private lateinit var entitySandboxService: EntitySandboxService
    private lateinit var processor: EntityMessageProcessor

    private lateinit var virtualNodeInfo: VirtualNodeInfo
    private lateinit var cpkFileHashes: Set<SecureHash>

    @InjectService
    lateinit var lbm: LiquibaseSchemaMigrator

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        logger.info("Setup test (test Directory: $testDirectory)")
        sandboxSetup.configure(bundleContext, testDirectory)
        sandboxLifecycle.accept(sandboxSetup) {
            virtualNode = sandboxSetup.fetchService(timeout = 5000)
            cpiInfoReadService = sandboxSetup.fetchService(timeout = 5000)
            cpkReadService = sandboxSetup.fetchService(timeout = 5000)
            virtualNodeInfoReadService = sandboxSetup.fetchService(timeout = 5000)
            responseFactory = sandboxSetup.fetchService(timeout = 5000)
        }

        virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)
        cpkFileHashes = cpiInfoReadService.getCpkFileHashes(virtualNodeInfo)
    }

    @BeforeEach
    fun setUpBeforeEach() {
        dbConnectionManager = FakeDbConnectionManager(
            listOf(Pair(virtualNodeInfo.vaultDmlConnectionId, "animals-node")),
            "PersistenceExceptionTests"
        )
        entitySandboxService =
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpkReadService,
                virtualNodeInfoReadService,
                dbConnectionManager
            )
        processor = EntityMessageProcessor(
            currentSandboxGroupContext,
            entitySandboxService,
            responseFactory,
            this::noOpPayloadCheck
        )
    }

    @AfterEach
    fun cleanUp() {
        dbConnectionManager.stop()
    }

    @Test
    fun `exception raised when cpks not present`() {
        val ignoredRequest = createDogPersistRequest()

        // Insert some non-existent CPK hashes to our external event to trigger CPK not available error
        ignoredRequest.flowExternalEventContext.contextProperties = KeyValuePairList(
            mutableListOf(
                KeyValuePair("$CPK_FILE_CHECKSUM.0", "SHA-256:DEADBEEF")
            )
        )

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), ignoredRequest)))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.TRANSIENT)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(CpkNotAvailableException::class.java.name)
    }

    @Test
    fun `exception raised when vnode cannot be found`() {
        val ignoredRequest = createDogPersistRequest()

        val brokenVirtualNodeInfoReadService = object :
            VirtualNodeInfoReadService by virtualNodeInfoReadService {
            override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
                throw VirtualNodeException("Placeholder")
            }
        }

        val brokenEntitySandboxService =
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpkReadService,
                brokenVirtualNodeInfoReadService,
                dbConnectionManager
            )

        val processor = EntityMessageProcessor(
            currentSandboxGroupContext,
            brokenEntitySandboxService,
            responseFactory,
            this::noOpPayloadCheck
        )

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), ignoredRequest)))

        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.TRANSIENT)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(VirtualNodeException::class.java.name)
    }

    @Test
    fun `exception raised when sent a missing command`() {
        val oldRequest = createDogPersistRequest()
        val unknownCommand = ExceptionEnvelope("", "") // Any Avro object, or null works here.

        val badRequest =
            EntityRequest(
                oldRequest.holdingIdentity,
                unknownCommand,
                ExternalEventContext(
                    "request id",
                    "flow id",
                    cpkFileHashes.toKeyValuePairList(CPK_FILE_CHECKSUM)
                )
            )

        // Now "send" the request for processing and "receive" the responses.
        val responses = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), badRequest)))


        assertThat(responses.size).isEqualTo(1)
        val flowEvent = responses.first().value as FlowEvent
        val response = flowEvent.payload as ExternalEventResponse
        assertThat(response.error).isNotNull
        // The failure is correctly categorised.
        assertThat(response.error.errorType).isEqualTo(ExternalEventResponseErrorType.FATAL)
        // The failure also captures the exception name.
        assertThat(response.error.exception.errorType).isEqualTo(CordaRuntimeException::class.java.name)
    }

    @Disabled("This test is disabled for now because currently we do execute duplicate persistence requests." +
            "It should be re-enabled after deduplication work is done in epic CORE-5909")
    @Test
    fun `on duplicate persistence request don't execute it - with PK constraint does not throw PK violation`() {
        createDogDb()
        val persistEntitiesRequest = createDogPersistRequest()

        val record1 = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), persistEntitiesRequest)))
        assertNull(((record1.single().value as FlowEvent).payload as ExternalEventResponse).error)
        // duplicate request
        val record2 = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), persistEntitiesRequest)))
        // The below should not contain a PK violation error as it should be identified it is the same persistence request
        // and therefore not executed
        assertNull(((record2.single().value as FlowEvent).payload as ExternalEventResponse).error)
    }

    @Disabled("This test is disabled for now because currently we do execute duplicate persistence requests." +
            "It should be re-enabled after deduplication work is done in epic CORE-5909")
    @Test
    fun `on duplicate persistence request don't execute it - without PK constraint does not add duplicate DB entry`() {
        createDogDb(DOGS_TABLE_WITHOUT_PK)
        val persistEntitiesRequest = createDogPersistRequest()

        val record1 = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), persistEntitiesRequest)))
        assertNull(((record1.single().value as FlowEvent).payload as ExternalEventResponse).error)
        // duplicate request
        val record2 = processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), persistEntitiesRequest)))
        assertNull(((record2.single().value as FlowEvent).payload as ExternalEventResponse).error)

        val dogDbCount = getDogDbCount(virtualNodeInfo.vaultDmlConnectionId)
        // There shouldn't be a dog duplicate entry in the DB, i.e. dogs count in the DB should still be 1
        assertEquals(1, dogDbCount)
    }

    @Disabled("This test is disabled for now because currently we do execute duplicate persistence requests." +
            "It should be re-enabled after deduplication work is done in epic CORE-5909")
    @Test
    fun `on duplicate persistence request don't execute it - statically updated field isn't getting updated in DB`() {
        createVersionedDogDb()
        val persistEntitiesRequest = createVersionedDogPersistRequest()

        // persist request
        processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), persistEntitiesRequest)))

        val serialisedDog = (persistEntitiesRequest.request as PersistEntities).entities

        val requestId = UUID.randomUUID().toString()
        val mergeEntityRequest =
            EntityRequest(
                virtualNodeInfo.holdingIdentity.toAvro(),
                MergeEntities(serialisedDog),
                ExternalEventContext(
                    requestId,
                    "flow id",
                    KeyValuePairList(
                        cpkFileHashes.map { KeyValuePair(CPK_FILE_CHECKSUM, it.toString()) }
                    )
                )
            )

        // first update request
        processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), mergeEntityRequest)))
        // check we update same dog
        val dogDbCount = getDogDbCount(virtualNodeInfo.vaultDmlConnectionId, dogDBTable = "versionedDog")
        assertEquals(1, dogDbCount)
        // check timestamp 1
        val dogVersion1 = getDogDbVersion(virtualNodeInfo.vaultDmlConnectionId)

        // duplicate update request
        processor.onNext(listOf(Record(TOPIC, UUID.randomUUID().toString(), mergeEntityRequest)))
        // check we update same dog
        val dogDbCount2 = getDogDbCount(virtualNodeInfo.vaultDmlConnectionId, dogDBTable = "versionedDog")
        assertEquals(1, dogDbCount2)
        // check timestamp 2
        val dogVersion2 = getDogDbVersion(virtualNodeInfo.vaultDmlConnectionId)
        assertEquals(dogVersion1, dogVersion2)
    }

    private fun noOpPayloadCheck(bytes: ByteBuffer) = bytes

    private fun createVersionedDogPersistRequest(): EntityRequest {
        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity, cpkFileHashes)
        // create dog using dog-aware sandbox
        val dog = sandbox.createVersionedDog("Stray", owner = "Not Known")
        val serialisedDog = sandbox.getSerializationService().serialize(dog).bytes
        return createPersistEntitiesRequest(listOf(ByteBuffer.wrap(serialisedDog)))
    }

    private fun createDogPersistRequest(): EntityRequest {
        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity, cpkFileHashes)
        // create dog using dog-aware sandbox
        val dog = sandbox.createDog("Stray", owner = "Not Known").instance
        val serialisedDog = sandbox.getSerializationService().serialize(dog).bytes
        return createPersistEntitiesRequest(listOf(ByteBuffer.wrap(serialisedDog)))
    }

    private fun createPersistEntitiesRequest(serializedEntities: List<ByteBuffer>): EntityRequest {
        // create persist request for the sandbox that isn't dog-aware
        val requestId = UUID.randomUUID().toString()
        return EntityRequest(
            virtualNodeInfo.holdingIdentity.toAvro(),
            PersistEntities(serializedEntities),
            ExternalEventContext(
                requestId,
                "flow id",
                KeyValuePairList(
                    cpkFileHashes.map { KeyValuePair(CPK_FILE_CHECKSUM, it.toString()) }
                )
            )
        )
    }

    private fun createDogDb(liquibaseScript: String = DOGS_TABLE) {
        val sandboxGroupContext = entitySandboxService.get(virtualNodeInfo.holdingIdentity, cpkFileHashes)
        val dogClass = sandboxGroupContext.sandboxGroup.getDogClass()
        createDb(liquibaseScript, dogClass)
    }

    private fun createVersionedDogDb() {
        val sandboxGroupContext = entitySandboxService.get(virtualNodeInfo.holdingIdentity, cpkFileHashes)
        val versionedDog = sandboxGroupContext.sandboxGroup.getVersionedDogClass()
        createDb(VERSIONED_DOGS_TABLE, versionedDog)
    }

    private fun createDb(liquibaseScript: String, entityClass: Class<*>) {
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    entityClass.packageName,
                    listOf(liquibaseScript),
                    entityClass.classLoader
                )
            )
        )
        val ds = dbConnectionManager.getDataSource(virtualNodeInfo.vaultDmlConnectionId)
        ds.connection.use {
            lbm.updateDb(it, cl)
        }
    }

    private fun getDogDbCount(connectionId: UUID, dogDBTable: String = "dog"): Int =
        dbConnectionManager
            .getDataSource(connectionId).connection.use { connection ->
                connection.prepareStatement("SELECT count(*) FROM $dogDBTable").use {
                    it.executeQuery().use { rs ->
                        if (!rs.next()) {
                            throw IllegalStateException("Should be able to find at least 1 dog entry")
                        }
                        rs.getInt(1)
                    }
                }
            }

    private fun getDogDbVersion(connectionId: UUID): Int =
        dbConnectionManager
            .getDataSource(connectionId).connection.use { connection ->
                connection.prepareStatement("SELECT version FROM versionedDog").use {
                    it.executeQuery().use { rs ->
                        if (!rs.next()) {
                            throw IllegalStateException("Should be able to find at least 1 dog entry")
                        }
                        rs.getInt(1)
                            .also {
                                if (rs.next()) {
                                    throw IllegalStateException("There should be at most 1 dog entry")
                                }
                            }
                    }
                }
            }
}
