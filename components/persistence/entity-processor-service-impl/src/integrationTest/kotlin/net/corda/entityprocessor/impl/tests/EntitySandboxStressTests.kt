package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpk.read.CpkReadService
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.fake.FakeDbConnectionManager
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
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


@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntitySandboxStressTests {

    private companion object {
        private const val TIMEOUT_MILLIS = 10000L
    }


    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var cpkReadService: CpkReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            cpkReadService = setup.fetchService(TIMEOUT_MILLIS)
            virtualNodeInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

//    @ParameterizedTest
//    @EnumSource(StressTestType::class)
//    @Timeout(value = 1, unit = TimeUnit.MINUTES)
//    fun `create entity sandboxes - no caching`(testType: StressTestType) {
//        // set cache size to 0
//        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.PERSISTENCE, 0)
//
//        // create vNodes
//        val vNodes = mutableListOf<VirtualNodeInfo>()
//        val connections = mutableListOf<Pair<UUID, String>>()
//        val schemaName = "PSIT${testType.numSandboxes}"
//        repeat(testType.numSandboxes) {
//            vNodes.add(virtualNode.load(Resources.EXTENDABLE_CPB))
//            connections.add(Pair(vNodes.last().vaultDmlConnectionId, it.toString()))
//        }
//
//        // create db connection manager and sandbox service
//        val dbConnectionManager = FakeDbConnectionManager(connections, schemaName)
//        val entitySandboxService = EntitySandboxServiceFactory().create(
//            virtualNode.sandboxGroupContextComponent,
//            cpkReadService,
//            virtualNodeInfoReadService,
//            dbConnectionManager
//        )
//
//        vNodes.forEach {
//            // create the sandbox
//            val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(it)
//            val sandbox = entitySandboxService.get(it.holdingIdentity, cpkFileHashes)
//            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n$sandbox")
//        }
//
//        dbConnectionManager.stop()
//    }

//    @ParameterizedTest
//    @EnumSource(value = StressTestType::class, names = ["TEN_SANDBOXES"])
//    @Timeout(value = 1, unit = TimeUnit.MINUTES)
//    fun `pull sandboxes out of large cash - 251`(testType: StressTestType) {
//        // set cache size
//        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.PERSISTENCE, 251)
//
//        // create vNodes
//        val vNodes = mutableListOf<VirtualNodeInfo>()
//        val connections = mutableListOf<Pair<UUID, String>>()
//        val schemaName = "PSIT${testType.numSandboxes}"
//        repeat(testType.numSandboxes) {
//            vNodes.add(virtualNode.load(Resources.EXTENDABLE_CPB))
//            connections.add(Pair(vNodes.last().vaultDmlConnectionId, it.toString()))
//        }
//
//        // create db connection manager and sandbox service
//        val dbConnectionManager = FakeDbConnectionManager(connections, schemaName)
//        val entitySandboxService = EntitySandboxServiceFactory().create(
//            virtualNode.sandboxGroupContextComponent,
//            cpkReadService,
//            virtualNodeInfoReadService,
//            dbConnectionManager
//        )
//
//        vNodes.forEach {
//            // create the sandbox
//            val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(it)
//            val sandbox = entitySandboxService.get(it.holdingIdentity, cpkFileHashes)
//            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n$sandbox")
//        }
//
//        // ensure no sandboxes have been evicted
//        var evictions = 0
//        virtualNode.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
//            evictions++
//            println("Virtual node ${it.holdingIdentity.shortHash} has been evicted")
//        }
//
//        assertThat(evictions==0)
//
//        // retrieve all sandboxes from the cache
//        vNodes.forEach {
//            // create the sandbox
//            val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(it)
//            val sandbox = entitySandboxService.get(it.holdingIdentity, cpkFileHashes)
//            println("Pulling sandbox for vNode ${it.holdingIdentity.shortHash}\n$sandbox")
//
//            // TODO: should probably exercise the sandbox somehow
//        }
//
//        println("Number of evictions = $evictions")
//
//        dbConnectionManager.stop()
//    }

    @ParameterizedTest
    @EnumSource(value = StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES"])
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `pull many sandboxes out of small cash - 10`(testType: StressTestType) {
        // set cache size
        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.PERSISTENCE, 10)

        // create vNodes
        val vNodes = mutableListOf<VirtualNodeInfo>()
        val connections = mutableListOf<Pair<UUID, String>>()
        val schemaName = "PSIT${testType.numSandboxes}"
        repeat(testType.numSandboxes) {
            vNodes.add(virtualNode.load(Resources.EXTENDABLE_CPB))
            connections.add(Pair(vNodes.last().vaultDmlConnectionId, it.toString()))
        }

        // create db connection manager and sandbox service
        val dbConnectionManager = FakeDbConnectionManager(connections, schemaName)
        val entitySandboxService = EntitySandboxServiceFactory().create(
            virtualNode.sandboxGroupContextComponent,
            cpkReadService,
            virtualNodeInfoReadService,
            dbConnectionManager
        )

        var evictions = 0
        virtualNode.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.PERSISTENCE) {
            evictions++
            println("Virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        vNodes.forEach {
            // create the sandbox
            val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(it)
            val sandbox = entitySandboxService.get(it.holdingIdentity, cpkFileHashes)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n$sandbox")
        }

        println("Number of evictions during sandbox creation = $evictions")
        assertThat(evictions == testType.numSandboxes - 10)

        // retrieve all sandboxes from the cache
//        vNodes.forEach {
//            // create the sandbox
//            val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(it)
//            val sandbox = entitySandboxService.get(it.holdingIdentity, cpkFileHashes)
//            println("Pulling sandbox for vNode ${it.holdingIdentity.shortHash}\n$sandbox")
//
//            // TODO: should probably exercise the sandbox somehow
//        }
//
//        println("Number of evictions = $evictions")

        dbConnectionManager.stop()
    }
    enum class StressTestType(val numSandboxes: Int, val testName: String) {
        TEN_SANDBOXES(10, "Create 10 sandboxes"),
        ONE_HUNDRED_SANDBOXES(100, "Create 100 sandboxes"),
        TWO_HUNDRED_FIFTY_SANDBOXES(250, "Create 250 sandboxes")
    }
}