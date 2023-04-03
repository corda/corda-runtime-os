package net.corda.sandboxgroupcontext.service.impl

import com.typesafe.config.ConfigException
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.read.CpkReadService
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.sandbox.SandboxCreationService
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheControl
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Collections.unmodifiableList
import java.util.concurrent.CompletableFuture

/**
 * Sandbox group context service component... with lifecycle, since it depends on a CPK service
 * that has a lifecycle.
 */
@Suppress("Unused", "LongParameterList")
@Component(service = [SandboxGroupContextComponent::class])
class SandboxGroupContextComponentImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SandboxCreationService::class)
    private val sandboxCreationService: SandboxCreationService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference
    private val sandboxGroupContextService: SandboxGroupContextService,
    @Reference
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext
) : SandboxGroupContextComponent, SandboxGroupContextService by sandboxGroupContextService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val PLATFORM_PUBLIC_BUNDLE_NAMES: List<String> = unmodifiableList(
            listOf(
                "co.paralleluniverse.quasar-core.framework.extension",
                "com.esotericsoftware.reflectasm",
                "javax.persistence-api",
                "jcl.over.slf4j",
                "net.corda.application",
                "net.corda.base",
                "net.corda.crypto",
                "net.corda.crypto-extensions",
                "net.corda.ledger-common",
                "net.corda.ledger-consensual",
                "net.corda.ledger-utxo",
                "net.corda.membership",
                "net.corda.notary-plugin",
                "net.corda.persistence",
                "net.corda.serialization",
                "org.apache.aries.spifly.dynamic.framework.extension",
                "org.apache.felix.framework",
                "org.hibernate.orm.core",
                "org.jetbrains.kotlin.osgi-bundle",
                "slf4j.api"
            )
        )

        // TODO - this isn't a sensible production default
        //  when configuration default handling is complete (CORE-3780), this should be moved
        //  and changed to a sensible default, while keeping 2 as a default for our test environments.
        //  2 is good for a test environment as it is likely to validate both caching and eviction.
        const val SANDBOX_CACHE_SIZE_DEFAULT = 2L

        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG = "CONFIG"
    }

    private val coordinator = coordinatorFactory.createCoordinator<SandboxGroupContextComponent>(::eventHandler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() = coordinator.stop()

    @Deactivate
    override fun close() {
        coordinator.close()
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
        val config = event.config.getConfig(ConfigKeys.SANDBOX_CONFIG)

        SandboxGroupType.values().forEach {
            val cacheSize = try {
                config.getConfig(it.name.lowercase()).getLong(ConfigKeys.SANDBOX_CACHE_SIZE)
            } catch (e: ConfigException.Missing) {
                SANDBOX_CACHE_SIZE_DEFAULT
            }

            logger.info("Re-creating Sandbox ${it.name} cache with size: {}", cacheSize)
            resizeCache(it, cacheSize)
        }

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            coordinator.createManagedResource(CONFIG) {
                configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.SANDBOX_CONFIG)
                )
            }
        }
    }

    private fun onStart(coordinator: LifecycleCoordinator) {
        logger.debug { "${javaClass.name} starting" }
        initialiseSandboxContext(bundleContext.bundles)
        coordinator.createManagedResource(REGISTRATION) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<CpkReadService>()
                )
            )
        }
    }

    private fun onStop() {
        logger.debug { "${javaClass.name} stopping" }
        sandboxGroupContextService.close()
    }

    private fun initialiseSandboxContext(allBundles: Array<Bundle>) {
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }

    override fun flushCache(): CompletableFuture<*> {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Sandbox cache could not be flushed")).flushCache()
    }

    @Throws(InterruptedException::class)
    override fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Sandbox cache unavailable for waiting")).waitFor(completion, duration)
    }

    override fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Sandbox could not be removed from cache")).remove(virtualNodeContext)
    }

    override fun initCache(type: SandboxGroupType, capacity: Long) {
        logger.info("Initialising Sandbox cache with capacity: {}", capacity)
        resizeCache(type, capacity)
    }

    private fun resizeCache(type: SandboxGroupType, capacity: Long) {
        (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Sandbox $type cache could not be resized to $capacity")).initCache(type, capacity)
    }
}
