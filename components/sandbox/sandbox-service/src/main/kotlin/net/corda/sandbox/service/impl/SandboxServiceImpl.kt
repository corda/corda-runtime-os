package net.corda.sandbox.service.impl

import net.corda.install.InstallService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.service.SandboxService
import net.corda.sandbox.service.helper.initPublicSandboxes
import net.corda.serialization.CheckpointSerializer
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SandboxService::class])
class SandboxServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = InstallService::class)
    private val installService: InstallService,
    @Reference(service = SandboxCreationService::class)
    private val sandboxCreationService: SandboxCreationService,
    @Reference(service = ConfigurationAdmin::class)
    private val configurationAdmin: ConfigurationAdmin,
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory,
) : SandboxService {

    companion object {
        private val logger = contextLogger()

        //tmp stuff until cpi service is available
        private val configuredBaseDir: String? = System.getProperty("base.directory")
        private val cpbDirPath: String? = System.getProperty("cpb.directory")
        private val baseDirectory = if (!configuredBaseDir.isNullOrEmpty()) {
            Paths.get(URI.create(configuredBaseDir))
        } else {
            Paths.get("")
        }.toAbsolutePath().toString()
    }

    private val checkpointSerializers = mutableMapOf<SandboxGroup, CheckpointSerializer>()
    private var cache = ConcurrentHashMap<Pair<String, String>, SandboxGroup>()
    private var cpiForFlow = ConcurrentHashMap<String, CPI.Identifier>()
    private val coordinator = coordinatorFactory.createCoordinator<SandboxService>(::eventHandler)

    override fun getSandboxGroupFor(cpiId: String, identity: String, flowName: String): SandboxGroup {
        return cache.computeIfAbsent(Pair(cpiId, identity)) {
            //hacky stuff until cpi service component is available. e.g dummy load logic doesnt handle multiple groups
            val cpbIdentifier = cpiForFlow[flowName]
                ?: throw CordaRuntimeException("Flow not available in cordapp")
            val cpb = installService.getCpb(cpbIdentifier)
                ?: throw CordaRuntimeException("Could not get cpb from its identifier $cpbIdentifier")
            sandboxCreationService.createSandboxGroup(cpb.cpks)
        }
    }

    override fun getSerializerForSandbox(sandboxGroup: SandboxGroup): CheckpointSerializer {
        return checkpointSerializers.computeIfAbsent(sandboxGroup) {
            checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(it).build()
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "SandboxService received: $event" }
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting sandbox component." }
                initPublicSandboxes(configurationAdmin, sandboxCreationService, baseDirectory)
                cache = ConcurrentHashMap()
                cpiForFlow = ConcurrentHashMap()
                loadCpbs(getCPBFiles())
                coordinator.updateStatus(LifecycleStatus.UP, "Connected to configuration repository.")
            }
            is StopEvent -> {
                logger.debug { "Stopping sandbox component." }
                cache.clear()
                cpiForFlow.clear()
            }
        }
    }

    private fun getCPBFiles(): List<String> {
        if (cpbDirPath == null) {
            return emptyList()
        }
        return File(cpbDirPath).listFiles().filter { it.name.endsWith("cpb") }.map { it.toPath().toString() }
    }

    /**
     * Bit of a hack until we have a CPIService. Doesn't handle multiple identities
     */
    private fun loadCpbs(
        CPBs: List<String>,
    ) {
        for (cpbFile in CPBs) {
            val cpbInputStream = File(cpbFile).inputStream()
            val cpb = installService.loadCpb(cpbInputStream)
            val cpbEx = installService.getCpb(cpb.metadata.id)
            cpbEx?.cpks?.forEach { cpk ->
                cpk.metadata.cordappManifest.flows.forEach { flow ->
                    cpiForFlow.computeIfAbsent(flow) { cpb.metadata.id }
                }
            }
        }
    }

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
