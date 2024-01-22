package net.corda.testing.sandboxes.stresstests

import net.corda.cpk.read.CpkReadService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.stresstests.utils.StressTestType
import net.corda.testing.sandboxes.stresstests.utils.TestBase
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
import java.util.UUID
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
            virtualNodeService = setup.fetchService(TIMEOUT_MILLIS)
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            cpkReadService = setup.fetchService(TIMEOUT_MILLIS)
            virtualNodeInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    fun prepareTest(testType: StressTestType) {
        createVnodes(testType.numSandboxes, true)
        val connections = mutableListOf<Pair<UUID, String>>()
        val schemaName = "PSIT${testType.numSandboxes}"
        vNodes.forEach {
            connections.add(Pair(it.vaultDmlConnectionId, it.holdingIdentity.shortHash.value))
        }

        println("Creating db connection manager")

        // create db connection manager and sandbox service
        dbConnectionManager = FakeDbConnectionManager(connections, schemaName)
        println("Creating entity sandbox service")
        entitySandboxService = EntitySandboxServiceFactory().create(
            virtualNodeService.sandboxGroupContextComponent,
            cpkReadService,
            virtualNodeInfoReadService,
            dbConnectionManager
        )
        println("Created entity sandbox service")
    }

    @AfterEach
    fun tearDownTest() {
        dbConnectionManager.stop()
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `create entity sandboxes - no caching`(testType: StressTestType) {
        prepareTest(testType)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        vNodes.forEach {
            // create the sandbox
            val sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }

        assertThat(evictions).isEqualTo(testType.numSandboxes)
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
        retrieveSandboxes(testType, 10, testType.numSandboxes - 10)
    }

    private fun retrieveSandboxes(testType: StressTestType, cacheSize: Long, numberOfEvictions: Int) {
        // set cache size
        virtualNodeService.sandboxGroupContextComponent.resizeCache(SandboxGroupType.PERSISTENCE, cacheSize)

        prepareTest(testType)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
            evictions++
            println("Virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        vNodes.forEach {
            // create the sandbox
            val sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }

        assertThat(evictions).isEqualTo(numberOfEvictions)

        // retrieve all sandboxes from the cache
        val sandboxes = mutableSetOf<UUID>()
        vNodes.forEach {
            val sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            sandboxes.add(sandbox.sandboxGroup.id)
            println("Retrieving sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")

            // TODO: should probably exercise the sandbox somehow
        }

        assertThat(sandboxes.size).isEqualTo(testType.numSandboxes)
    }
}