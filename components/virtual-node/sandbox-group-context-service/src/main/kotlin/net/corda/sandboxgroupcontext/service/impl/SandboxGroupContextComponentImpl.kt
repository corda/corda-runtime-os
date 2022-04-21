package net.corda.sandboxgroupcontext.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.schema.configuration.ConfigKeys
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
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
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

        // TODO - this isn't a sensible production default
        //  when configuration default handling is complete (CORE-3780), this should be moved
        //  and changed to a sensible default, while keeping 2 as a default for our test environments.
        //  2 is good for a test environment as it is likely to validate both caching and eviction.
        const val SANDBOX_CACHE_SIZE_DEFAULT: Long = 2
    }

    private var sandboxGroupContextService: SandboxGroupContextServiceImpl? = null
    private val coordinator = coordinatorFactory.createCoordinator<SandboxGroupContextComponent>(::eventHandler)
    private var registrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext, initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext =
        sandboxGroupContextService?.getOrCreate(virtualNodeContext, initializer)?:
            throw IllegalStateException("SandboxGroupContextService is not ready.")


    override fun registerMetadataServices(
        sandboxGroupContext: SandboxGroupContext,
        serviceNames: (CpkMetadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean,
        serviceMarkerType: Class<*>
    ): AutoCloseable =
        sandboxGroupContextService?.registerMetadataServices(
            sandboxGroupContext, serviceNames, isMetadataService, serviceMarkerType
        )?:
            throw IllegalStateException("SandboxGroupContextService is not ready.")

    override fun registerCustomCryptography(
        sandboxGroupContext: SandboxGroupContext
    ): AutoCloseable =
        sandboxGroupContextService?.registerCustomCryptography(sandboxGroupContext)?:
            throw IllegalStateException("SandboxGroupContextService is not ready.")

    override fun hasCpks(cpkIdentifiers: Set<CpkIdentifier>): Boolean =
        sandboxGroupContextService?.hasCpks(cpkIdentifiers)?:
        throw IllegalStateException("SandboxGroupContextService is not ready.")

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
        sandboxGroupContextService?.close()
        cpkReadService.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "${javaClass.name} received: $event" }
        when (event) {
            is StartEvent -> onStart(coordinator)
            is StopEvent -> onStop()
            is RegistrationStatusChangeEvent -> onRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangeEvent(event, coordinator)
        }
    }

    private fun onConfigChangeEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        //Hack:this can be put back after CORE-3780 as we would always get the config key with the default value
        // if(event.keys.contains(ConfigKeys.SANDBOX_CONFIG)) {

            // Hack: Can be removed when default handling is part of CORE-3780
            val cacheSize = if(event.keys.contains(ConfigKeys.SANDBOX_CONFIG) && event.config[ConfigKeys.SANDBOX_CONFIG]!!.hasPath(ConfigKeys.SANDBOX_CACHE_SIZE)) {
                event.config[ConfigKeys.SANDBOX_CONFIG]!!.getLong(ConfigKeys.SANDBOX_CACHE_SIZE)
            } else {
                SANDBOX_CACHE_SIZE_DEFAULT
            }

            if(null == sandboxGroupContextService)
                initCache(cacheSize)
            else if (sandboxGroupContextService!!.cache.cacheSize != cacheSize) {
                // this means the cache size has been reconfigured, which means we need to recreate the cache
                logger.info("Re-creating Sandbox cache with size: $cacheSize")
                val oldCache = sandboxGroupContextService!!.cache
                sandboxGroupContextService!!.cache = SandboxGroupContextCacheImpl(cacheSize)
                oldCache.close()
            }

            coordinator.updateStatus(LifecycleStatus.UP)
        // }
    }

    private fun onRegistrationChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            configHandle = configurationReadService.registerComponentForUpdates(
                coordinator,
                //Hack: Needs to be reviewed as part of CORE-3780
                //  This will wait for all keys and without the default handling, this may never happen
                //  (unless you specifically configure it)
                setOf(ConfigKeys.BOOT_CONFIG /*, ConfigKeys.SANDBOX_CONFIG */)
            )
        } else {
            configHandle?.close()
        }
    }

    private fun onStart(coordinator: LifecycleCoordinator) {
        logger.debug { "${javaClass.name} starting" }
        initialiseSandboxContext(bundleContext.bundles)
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<CpkReadService>()
            )
        )
    }

    private fun onStop() {
        logger.debug { "${javaClass.name} stopping" }
        sandboxGroupContextService?.close()
        sandboxGroupContextService = null
        registrationHandle?.close()
        registrationHandle = null
        coordinator.stop()
    }

    private fun initialiseSandboxContext(allBundles: Array<Bundle>) {
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }

    override fun initCache(cacheSize: Long) {
        logger.info("Initialising Sandbox cache with size: $cacheSize")
        sandboxGroupContextService = SandboxGroupContextServiceImpl(
            sandboxCreationService,
            cpkReadService,
            serviceComponentRuntime,
            bundleContext,
            SandboxGroupContextCacheImpl(cacheSize)
        )
    }
}
