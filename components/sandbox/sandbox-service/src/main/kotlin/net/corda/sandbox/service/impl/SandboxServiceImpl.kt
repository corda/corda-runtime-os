package net.corda.sandbox.service.impl

import net.corda.install.InstallService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.service.SandboxService
import net.corda.sandbox.service.helper.initPublicSandboxes
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.net.URI
import java.nio.file.Paths

@Suppress("UNUSED")
@Component(service = [SandboxService::class])
class SandboxServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = InstallService::class)
    private val installService: InstallService,
    @Reference(service = SandboxCreationService::class)
    private val sandboxCreationService: SandboxCreationService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextService: SandboxGroupContextComponent,
    @Reference(service = ConfigurationAdmin::class)
    private val configurationAdmin: ConfigurationAdmin
) : SandboxService {
    companion object {
        private val logger = contextLogger()

        //tmp stuff until cpi service is available
        private val configuredBaseDir: String? = System.getProperty("base.directory")
        private val baseDirectory = if (!configuredBaseDir.isNullOrEmpty()) {
            Paths.get(URI.create(configuredBaseDir))
        } else {
            Paths.get("")
        }.toAbsolutePath().toString()
    }

    private val coordinator = coordinatorFactory.createCoordinator<SandboxService>(::eventHandler)
    private var registrationHandle: RegistrationHandle? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStart(coordinator)
            is StopEvent -> onStop()
            is RegistrationStatusChangeEvent -> onRegistrationChangeEvent(event, coordinator)
        }
    }

    private fun onRegistrationChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            coordinator.stop()
        }
    }

    private fun onStart(coordinator: LifecycleCoordinator) {
        logger.debug { "${javaClass.name} starting" }
        initPublicSandboxes(configurationAdmin, sandboxCreationService, baseDirectory)
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<InstallService>(),
                LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>()
            )
        )
    }

    private fun onStop() {
        logger.debug { "${javaClass.name} stopping" }
        registrationHandle?.close()
        registrationHandle = null
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        installService.start()
        sandboxGroupContextService.start()
        coordinator.start()
    }

    override fun stop() = coordinator.stop()

    override fun getOrCreateByCpiIdentifier(
        holdingIdentity: HoldingIdentity,
        cpiIdentifier: CPI.Identifier,
        sandboxGroupType: SandboxGroupType,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        val cpb = installService.get(cpiIdentifier).get()
            ?: throw CordaRuntimeException("Could not get cpi from its identifier $cpiIdentifier")
        val identifiers = cpb.cpks.mapTo(LinkedHashSet()) { it.metadata.id }
        val virtualNodeContext = VirtualNodeContext(holdingIdentity, identifiers, sandboxGroupType)
        return sandboxGroupContextService.getOrCreate(virtualNodeContext, initializer)
    }

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        return sandboxGroupContextService.getOrCreate(virtualNodeContext, initializer)
    }

    override fun hasCpks(cpkIdentifiers: Set<CPK.Identifier>): Boolean = sandboxGroupContextService.hasCpks(cpkIdentifiers)
}
