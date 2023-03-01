package net.corda.sandboxgroupcontext.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.read.CpkReadService
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
import net.corda.sandbox.SandboxCreationService
import net.corda.sandboxgroupcontext.SandboxGroupContextService
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
@Component(service = [ SandboxGroupContextComponent::class ])
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
    }

    private val coordinator = coordinatorFactory.createCoordinator<SandboxGroupContextComponent>(::eventHandler)
    private var registrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

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
        //Hack:this can be put back after CORE-3780 as we would always get the config key with the default value
        // if(event.keys.contains(ConfigKeys.SANDBOX_CONFIG)) {

            // Hack: Can be removed when default handling is part of CORE-3780
            val cacheSize = if(
                event.keys.contains(ConfigKeys.SANDBOX_CONFIG)
                && event.config[ConfigKeys.SANDBOX_CONFIG]!!.hasPath(ConfigKeys.SANDBOX_CACHE_SIZE)) {
                event.config[ConfigKeys.SANDBOX_CONFIG]!!.getLong(ConfigKeys.SANDBOX_CACHE_SIZE)
            } else {
                SANDBOX_CACHE_SIZE_DEFAULT
            }

            logger.info("Re-creating Sandbox cache with size: {}", cacheSize)
            resizeCache(cacheSize)

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
        sandboxGroupContextService.close()
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

    override fun initCache(capacity: Long) {
        logger.info("Initialising Sandbox cache with capacity: {}", capacity)
        resizeCache(capacity)
    }

    private fun resizeCache(capacity: Long) {
        (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Sandbox cache could not be resized to $capacity")).initCache(capacity)
    }
}
