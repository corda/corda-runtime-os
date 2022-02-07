package net.corda.sandboxgroupcontext.service.impl

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
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxCreationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.helper.initPublicSandboxes
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.framework.BundleContext
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime
import java.nio.file.Paths

/**
 * Sandbox group context service component... with lifecycle, since it depends on a CPK service
 * that has a lifecycle.
 */
@Suppress("Unused", "LongParameterList")
@Component(service = [SandboxGroupContextComponent::class])
class SandboxGroupContextComponentImpl @Activate constructor(
    @Reference(service = InstallService::class)
    private val installService: InstallService,
    @Reference(service = SandboxCreationService::class)
    private val sandboxCreationService: SandboxCreationService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationAdmin::class)
    private val configurationAdmin: ConfigurationAdmin,
    @Reference
    private val serviceComponentRuntime: ServiceComponentRuntime,
    bundleContext: BundleContext
) : SandboxGroupContextComponent {
    companion object {
        private val logger = contextLogger()
    }

    private val sandboxGroupContextServiceImpl = SandboxGroupContextServiceImpl(
        sandboxCreationService,
        installService,
        serviceComponentRuntime,
        bundleContext
    )
    private val coordinator = coordinatorFactory.createCoordinator<SandboxGroupContextComponent>(::eventHandler)
    private var registrationHandle: RegistrationHandle? = null

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext, initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext = sandboxGroupContextServiceImpl.getOrCreate(virtualNodeContext, initializer)

    override fun registerMetadataServices(
        sandboxGroupContext: SandboxGroupContext,
        serviceNames: (CPK.Metadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean
    ): AutoCloseable = sandboxGroupContextServiceImpl.registerMetadataServices(sandboxGroupContext, serviceNames, isMetadataService)

    override fun hasCpks(cpkIdentifiers: Set<CPK.Identifier>): Boolean =
        sandboxGroupContextServiceImpl.hasCpks(cpkIdentifiers)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        installService.start()
        coordinator.start()
    }

    override fun stop() = coordinator.stop()

    override fun close() {
        stop()
        coordinator.close()
        sandboxGroupContextServiceImpl.close()
    }

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
        initialiseSandboxContext()
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<InstallService>()
            )
        )
    }

    private fun onStop() {
        logger.debug { "${javaClass.name} stopping" }
        registrationHandle?.close()
        registrationHandle = null
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun initialiseSandboxContext(){
        val baseDirectory = Paths.get(System.getProperty("base.directory")?: "").toAbsolutePath().toString()
        initPublicSandboxes(configurationAdmin, sandboxCreationService, baseDirectory)
    }
}
