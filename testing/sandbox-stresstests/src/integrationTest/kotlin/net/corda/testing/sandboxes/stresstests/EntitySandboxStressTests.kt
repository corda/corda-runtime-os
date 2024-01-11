package net.corda.testing.sandboxes.stresstests

import net.corda.cpk.read.CpkReadService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntitySandboxStressTests : TestBase() {

    private lateinit var cpkReadService: CpkReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var entitySandboxService: EntitySandboxService
    private lateinit var dbConnectionManager: FakeDbConnectionManager

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        this.bundleContext = bundleContext
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            cpkReadService = setup.fetchService(TIMEOUT_MILLIS)
            virtualNodeInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    fun prepareTest(testType: StressTestType) {
        createVnodes(testType.numSandboxes)
        val connections = mutableListOf<Pair<UUID, String>>()
        val schemaName = "PSIT${testType.numSandboxes}"
        vNodes.forEach {
            connections.add(Pair(it.vaultDmlConnectionId, it.holdingIdentity.shortHash.value))
        }

        // create db connection manager and sandbox service
        dbConnectionManager = FakeDbConnectionManager(connections, schemaName)
        entitySandboxService = EntitySandboxServiceFactory().create(
            virtualNode.sandboxGroupContextComponent,
            cpkReadService,
            virtualNodeInfoReadService,
            dbConnectionManager
        )
    }

    @AfterEach
    fun tearDownTest() {
        dbConnectionManager.stop()
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `create entity sandboxes - no caching`(testType: StressTestType) {
        // set cache size to 0
        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.PERSISTENCE, 0)

        prepareTest(testType)

        vNodes.forEach {
            // create the sandbox
            val sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }
    }

    @ParameterizedTest
    @EnumSource(value = StressTestType::class)
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes from cache - size 251`(testType: StressTestType) {
        retrieveSandboxes(testType, 251, 0)
    }

    @ParameterizedTest
    @EnumSource(value = StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES", "TWO_HUNDRED_FIFTY_SANDBOXES"])
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes from cache - size 10`(testType: StressTestType) {
        retrieveSandboxes(testType, 10, 90)
    }

    private fun retrieveSandboxes(testType: StressTestType, cacheSize: Long, numberOfEvictions: Int) {
        // set cache size
        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.PERSISTENCE, cacheSize)

        prepareTest(testType)

        // track evictions
        var evictions = 0
        virtualNode.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
            evictions++
            println("Virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        vNodes.forEach {
            // create the sandbox
            val sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }

        assertThat(evictions==numberOfEvictions)

        // retrieve all sandboxes from the cache
        vNodes.forEach {
            val sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            println("Retrieving sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")

            // TODO: should probably exercise the sandbox somehow
        }

        // no evictions should have happened when retrieving the sandboxes
        assertThat(evictions==numberOfEvictions * 2)
    }
}