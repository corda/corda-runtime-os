package net.corda.testing.sandboxes.stresstests

import io.micrometer.core.instrument.Tag
import net.corda.cpk.read.CpkReadService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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

        // create db connection manager and sandbox service
        dbConnectionManager = FakeDbConnectionManager(connections, schemaName)
        entitySandboxService = EntitySandboxServiceFactory().create(
            virtualNodeService.sandboxGroupContextComponent,
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
        prepareTest(testType)

        // set-up file to dump metrics in
        val metricsFilePath = Paths.get("persistence_no-cache_${testType.testName}")
        val bw = Files.newBufferedWriter(metricsFilePath)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
            evictions++
            println("Persistence sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        // memory usage before creating sandboxes
        bw.write("### Memory usage before creating sandboxes")
        bw.newLine()
        bw.write(getMemoryUsage())
        bw.newLine()

        vNodes.forEach {
            var sandbox: SandboxGroupContext? = null
            prometheusMeterRegistry.timer("persistence.sandbox.create.time",
                listOf(Tag.of("virtual.node", it.holdingIdentity.shortHash.value))).recordCallable {
                sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            }
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash} ${sandbox!!.sandboxGroup.id}")

            bw.write("### Memory usage after creating sandbox with ID ${sandbox!!.sandboxGroup.id} " +
                    "for vNode ${it.holdingIdentity.shortHash}")
            bw.newLine()
            bw.write(getMemoryUsage())
            bw.newLine()
        }

        assertThat(evictions).isEqualTo(testType.numSandboxes)

        // add all measurements (mostly used to get the sandbox creation times)
        bw.write("### End of test metrics")
        bw.newLine()
        bw.write(prometheusMeterRegistry.scrape())
        bw.close()
    }

    @ParameterizedTest
    @EnumSource(value = StressTestType::class)
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes from cache - size 251`(testType: StressTestType) {
        // set-up file to dump metrics in
        val metricsFilePath = Paths.get("persistence_large-cache_${testType.testName}")
        retrieveSandboxes(metricsFilePath, testType, 251, 0)
    }

    @ParameterizedTest
    @EnumSource(value = StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES", "TWO_HUNDRED_FIFTY_SANDBOXES"])
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes from cache - size 10`(testType: StressTestType) {
        // set-up file to dump metrics in
        val metricsFilePath = Paths.get("persistence_small-cache_${testType.testName}")
        retrieveSandboxes(metricsFilePath, testType, 10, testType.numSandboxes - 10)
    }

    private fun retrieveSandboxes(metricsFilePath: Path, testType: StressTestType, cacheSize: Long, numberOfEvictions: Int) {
        // set cache size
        virtualNodeService.sandboxGroupContextComponent.resizeCache(SandboxGroupType.PERSISTENCE, cacheSize)

        prepareTest(testType)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
            evictions++
            println("Virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        val bw = Files.newBufferedWriter(metricsFilePath)
        // memory usage before creating sandboxes
        bw.write("### Memory usage before creating sandboxes")
        bw.newLine()
        bw.write(getMemoryUsage())
        bw.newLine()

        vNodes.forEach {
            var sandbox: SandboxGroupContext? = null

            prometheusMeterRegistry.timer("persistence.sandbox.create.time",
                listOf(Tag.of("virtual.node", it.holdingIdentity.shortHash.value))).recordCallable {
                sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            }

            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox!!.sandboxGroup.id}")

            bw.write("### Memory usage after creating sandbox with ID ${sandbox!!.sandboxGroup.id} " +
                    "for vNode ${it.holdingIdentity.shortHash}")
            bw.newLine()
            bw.write(getMemoryUsage())
            bw.newLine()
        }

        assertThat(evictions).isEqualTo(numberOfEvictions)

        // retrieve all sandboxes from the cache
        val sandboxes = mutableSetOf<UUID>()
        vNodes.forEach {
            var sandbox: SandboxGroupContext? = null

            prometheusMeterRegistry.timer("persistence.sandbox.retrieve.time",
                listOf(Tag.of("virtual.node", it.holdingIdentity.shortHash.value))).recordCallable {
                sandbox = getOrCreateSandbox(entitySandboxService::get, it)
            }

            sandboxes.add(sandbox!!.sandboxGroup.id)
            println("Retrieving sandbox ${sandbox!!.sandboxGroup.id} for virtual node ${it.holdingIdentity.shortHash}")

            bw.write("### Memory usage after retrieving sandbox with ID ${sandbox!!.sandboxGroup.id} " +
                    "for vNode ${it.holdingIdentity.shortHash}")
            bw.newLine()
            bw.write(getMemoryUsage())
            bw.newLine()
        }

        assertThat(sandboxes.size).isEqualTo(testType.numSandboxes)

        // add all measurements (mostly used to get the sandbox creation times)
        bw.write("### End of test metrics")
        bw.newLine()
        bw.write(prometheusMeterRegistry.scrape())
        bw.close()
    }
}