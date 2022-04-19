package net.corda.sandboxgroupcontext.service.impl

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.CpkIdentifier
import net.corda.libs.packaging.CpkMetadata
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
import net.corda.sandbox.RequireSandboxHooks
import net.corda.sandbox.SandboxCreationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime
import java.util.Collections.unmodifiableList

/**
 * Sandbox group context service component... with lifecycle, since it depends on a CPK service
 * that has a lifecycle.
 */
@Suppress("Unused", "LongParameterList")
@Component(service = [SandboxGroupContextComponent::class])
@RequireSandboxHooks
class SandboxGroupContextComponentImpl @Activate constructor(
    @Reference(service = CpkReadService::class)
    private val cpkReadService: CpkReadService,
    @Reference(service = SandboxCreationService::class)
    private val sandboxCreationService: SandboxCreationService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext
) : SandboxGroupContextComponent {
    companion object {
        private val logger = contextLogger()

        private val PLATFORM_PUBLIC_BUNDLE_NAMES: List<String> = unmodifiableList(
            listOf(
                "javax.persistence-api",
                "jcl.over.slf4j",
                "net.corda.application",
                "net.corda.base",
                "net.corda.cipher-suite",
                "net.corda.crypto",
                "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
                "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
                "net.corda.membership",
                "net.corda.persistence",
                "net.corda.serialization",
                "net.corda.services",
                "org.apache.aries.spifly.dynamic.bundle",
                "org.apache.felix.framework",
                "org.apache.felix.scr",
                "org.hibernate.orm.core",
                "org.jetbrains.kotlin.osgi-bundle",
                "slf4j.api"
            )
        )
    }

    private val sandboxGroupContextServiceImpl = SandboxGroupContextServiceImpl(
        sandboxCreationService,
        cpkReadService,
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
        serviceNames: (CpkMetadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean,
        serviceMarkerType: Class<*>
    ): AutoCloseable = sandboxGroupContextServiceImpl.registerMetadataServices(
        sandboxGroupContext, serviceNames, isMetadataService, serviceMarkerType
    )

    override fun registerCustomCryptography(
        sandboxGroupContext: SandboxGroupContext
    ): AutoCloseable = sandboxGroupContextServiceImpl.registerCustomCryptography(sandboxGroupContext)

    override fun hasCpks(cpkIdentifiers: Set<CpkIdentifier>): Boolean =
        sandboxGroupContextServiceImpl.hasCpks(cpkIdentifiers)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        cpkReadService.start()
        coordinator.start()
    }

    override fun stop() = coordinator.stop()

    override fun close() {
        stop()
        coordinator.close()
        sandboxGroupContextServiceImpl.close()
        cpkReadService.stop()
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
        initialiseSandboxContext(bundleContext.bundles)
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<CpkReadService>()
            )
        )
    }

    private fun onStop() {
        logger.debug { "${javaClass.name} stopping" }
        registrationHandle?.close()
        registrationHandle = null
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun initialiseSandboxContext(allBundles: Array<Bundle>) {
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }
}
