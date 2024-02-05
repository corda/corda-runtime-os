package net.corda.testing.sandboxes.stresstests

import io.micrometer.core.instrument.Tag
import net.corda.cpk.read.CpkReadService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.stresstests.utils.MeasurementType
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
import kotlin.test.Ignore

@Ignore
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
        noCacheMetricsWriter = Files.newBufferedWriter(Paths.get("persistence_no_cache.txt"))
        smallCacheMetricsWriter = Files.newBufferedWriter(Paths.get("persistence_small_cache.txt"))
        largeCacheMetricsWriter = Files.newBufferedWriter(Paths.get("persistence_large_cache.txt"))

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
    fun `create entity sandboxes - no cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes, true)
        prepareTest(testType)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
            evictions++
            println("Persistence sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        // memory usage before creating sandboxes
        noCacheMetricsWriter.write("### Memory usage before creating sandboxes - ${testType.testName}")
        noCacheMetricsWriter.newLine()
        noCacheMetricsWriter.write(getMeasurements(MeasurementType.MEMORY))
        noCacheMetricsWriter.newLine()

        prometheusMeterRegistry.timer("flow.sandbox.create.time",
            listOf(Tag.of("test.type", testType.name))).recordCallable {

            vNodes.forEach {
                val sandbox = getOrCreateSandbox(entitySandboxService::get, it)
                println("Created sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
            }
        }

        noCacheMetricsWriter.write("### Memory usage after creating sandboxes - ${testType.testName}")
        noCacheMetricsWriter.newLine()
        noCacheMetricsWriter.write(getMeasurements(MeasurementType.MEMORY))
        noCacheMetricsWriter.newLine()

        assertThat(evictions).isEqualTo(testType.numSandboxes)

        noCacheMetricsWriter.write("### Sandbox manipulation times - ${testType.numSandboxes}")
        noCacheMetricsWriter.newLine()
        noCacheMetricsWriter.write(getMeasurements(MeasurementType.SANDBOX_TIMES))
        noCacheMetricsWriter.newLine()
    }

    @ParameterizedTest
    @EnumSource(value = StressTestType::class)
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes using large cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes, true)
        prepareTest(testType)
        executeTest(largeCacheMetricsWriter,
            SandboxGroupType.PERSISTENCE,
            entitySandboxService::get,
            251,
            testType,
            0)
    }

    @ParameterizedTest
    @EnumSource(value = StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES", "TWO_HUNDRED_FIFTY_SANDBOXES"])
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes using small cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes, true)
        prepareTest(testType)
        executeTest(smallCacheMetricsWriter,
            SandboxGroupType.PERSISTENCE,
            entitySandboxService::get,
            10,
            testType,
            testType.numSandboxes - 10)
    }
}