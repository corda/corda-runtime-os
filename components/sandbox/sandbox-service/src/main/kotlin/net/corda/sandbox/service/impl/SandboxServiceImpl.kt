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
import net.corda.sandbox.service.SandboxType
import net.corda.sandbox.service.helper.initPublicSandboxes
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.sandboxgroup.MutableSandboxGroupContext
import net.corda.virtualnode.sandboxgroup.SandboxGroupContext
import net.corda.virtualnode.sandboxgroup.SandboxGroupService
import net.corda.virtualnode.sandboxgroup.VirtualNodeContext
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SandboxService::class, SandboxGroupService::class])
class SandboxServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = InstallService::class)
    private val installService: InstallService,
    @Reference(service = SandboxCreationService::class)
    private val sandboxCreationService: SandboxCreationService,
    @Reference(service = ConfigurationAdmin::class)
    private val configurationAdmin: ConfigurationAdmin
) : SandboxService, SandboxGroupService {

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

    private var cache = ConcurrentHashMap<SandboxCacheKey, SandboxGroup>()
    //tmp stuff until cpi service is available
    private var cpiIdentifierById = ConcurrentHashMap<String, CPI.Identifier>()
    private val coordinator = coordinatorFactory.createCoordinator<SandboxService>(::eventHandler)

    override fun getSandboxGroupFor(cpiId: String, identity: String, sandboxType: SandboxType): SandboxGroup {
        return cache.computeIfAbsent(SandboxCacheKey(identity, cpiId)) {
            //hacky stuff until cpi service component is available. e.g dummy load logic doesn't handle multiple groups
            val cpiIdentifier = cpiIdentifierById[cpiId]
                ?: throw CordaRuntimeException("Could not get cpi identifier")
            val cpb = installService.getCpb(cpiIdentifier)
                ?: throw CordaRuntimeException("Could not get cpi from its identifier $cpiIdentifier")
            sandboxCreationService.createSandboxGroup(cpb.cpks)
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
                cpiIdentifierById = ConcurrentHashMap()
                loadCpbs(getCPBFiles())
                coordinator.updateStatus(LifecycleStatus.UP, "Connected to configuration repository.")
            }
            is StopEvent -> {
                logger.debug { "Stopping sandbox component." }
                cache.clear()
                cpiIdentifierById.clear()
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
            val cpiIdentifier = cpb.metadata.id
            cpiIdentifierById[cpiIdentifier.name] = cpiIdentifier
        }
    }

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    data class SandboxCacheKey(val identity: String, val cpiId: String)

    override fun get(
        key: VirtualNodeContext,
        initializer: (holdingIdentity: HoldingIdentity, sandboxGroupContext: MutableSandboxGroupContext) -> AutoCloseable
    ): SandboxGroupContext {

        var cpi  = key.cpiIdentifier
        var holdingIdentity = key.holdingIdentity
        var context =  SimpleSandboxGroupContext(
            key,
            getSandboxGroupFor(cpi.name, holdingIdentity.x500Name, SandboxType.FLOW))

        initializer(holdingIdentity,context)

        return context
    }


    class SimpleSandboxGroupContext(
        override val context: VirtualNodeContext,
        override val sandboxGroup: SandboxGroup
    ) : MutableSandboxGroupContext {

        private val contextState = Collections.synchronizedMap(mutableMapOf<String, Any>())

        override fun <T> put(key: String, value: T) {
            contextState[key] = value
        }

        override fun <T> get(key: String): T? {
            return uncheckedCast(contextState[key])
        }

        override fun close() {
        }
    }
}
