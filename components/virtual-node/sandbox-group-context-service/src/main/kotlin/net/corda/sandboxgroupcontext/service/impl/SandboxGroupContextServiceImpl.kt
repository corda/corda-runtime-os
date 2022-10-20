@file:JvmName("SandboxGroupContextServiceUtils")
package net.corda.sandboxgroupcontext.service.impl

import java.security.AccessControlContext
import java.security.AccessControlException
import java.util.Collections.singleton
import java.util.Deque
import java.util.Hashtable
import java.util.LinkedList
import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.CORDA_SYSTEM_FILTER
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceObjects
import org.osgi.framework.ServicePermission
import org.osgi.framework.ServicePermission.GET
import org.osgi.framework.ServiceRegistration
import org.osgi.service.component.runtime.ServiceComponentRuntime

private typealias ServiceDefinition = Pair<ServiceObjects<out Any>, List<Class<*>>>

/**
 * This is the underlying implementation of the [SandboxGroupContextService]
 *
 * Use this service via the mutable and immutable interfaces to create a "virtual node",
 * and retrieve the same instance "later".
 *
 * This is a per-process service, but it must return the "same instance" for a given [VirtualNodeContext]
 * in EVERY process.
 */
class SandboxGroupContextServiceImpl(
    private val sandboxCreationService: SandboxCreationService,
    private val cpkReadService: CpkReadService,
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext,
    var cache: SandboxGroupContextCache
) : SandboxGroupContextService {
    private companion object {
        private const val SANDBOX_FACTORY_FILTER = "(&($SERVICE_SCOPE=$SCOPE_PROTOTYPE)(!$CORDA_SANDBOX_FILTER)(!$CORDA_SYSTEM_FILTER))"
        private const val SYSTEM_FACTORY_FILTER = "(&($SERVICE_SCOPE=$SCOPE_PROTOTYPE)(!$CORDA_SANDBOX_FILTER)$CORDA_SYSTEM_FILTER)"

        private val logger = loggerFor<SandboxGroupContextServiceImpl>()

        private val sandboxServiceProperties = Hashtable<String, Any?>().apply {
            put(CORDA_SANDBOX, true)
        }

        private fun <R> runIgnoringExceptions(action: () -> R) {
            try {
                action()
            } catch(e: Exception) {
                logger.warn("Ignoring exception", e)
            }
        }
    }

    fun remove(virtualNodeContext: VirtualNodeContext) {
        cache.remove(virtualNodeContext)
    }

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        return cache.get(virtualNodeContext) {
            val sandboxTimer = CordaMetrics.Metric.SandboxCreateTime.builder()
                .forVirtualNode(virtualNodeContext.holdingIdentity.shortHash.value)
                .withTag(CordaMetrics.Tag.SandboxGroupType, virtualNodeContext.sandboxGroupType.name)
                .build()
            sandboxTimer.recordCallable {
                val cpks = virtualNodeContext.cpkFileChecksums.mapNotNull { cpkReadService.get(it) }
                if (cpks.size != virtualNodeContext.cpkFileChecksums.size) {
                    logger.error("Not all CPKs could be retrieved for this virtual node context ($virtualNodeContext)")
                    logger.error("Wanted all of:  ${virtualNodeContext.cpkFileChecksums}")
                    val receivedIdentifiers = cpks.map { it.metadata.cpkId }
                    val missing = setOf(virtualNodeContext.cpkFileChecksums) - setOf(receivedIdentifiers)
                    logger.error("Returned:  $receivedIdentifiers")
                    logger.error("Missing:  $missing")
                    throw CordaRuntimeException("Not all CPKs could be retrieved for this virtual node context ($virtualNodeContext)\"")
                }

                val sandboxGroup =
                    sandboxCreationService.createSandboxGroup(cpks, virtualNodeContext.sandboxGroupType.name)

                // Default implementation doesn't do anything on close()`
                val sandboxGroupContext = SandboxGroupContextImpl(virtualNodeContext, sandboxGroup)

                // Register common OSGi services for use within this sandbox.
                val commonServiceRegistrations = registerCommonServices(virtualNodeContext, sandboxGroup.metadata.keys)

                // Run the caller's initializer.
                val initializerAutoCloseable =
                    initializer.initializeSandboxGroupContext(virtualNodeContext.holdingIdentity, sandboxGroupContext)

                // Wrapped SandboxGroupContext, specifically to set closeable and forward on all other calls.

                // Calling close also removes us from the contexts map and unloads the [SandboxGroup].
                val newContext: CloseableSandboxGroupContext = CloseableSandboxGroupContextImpl(sandboxGroupContext) {
                    // These objects might still be in a sandbox, so close them whilst the sandbox is still valid.
                    initializerAutoCloseable.close()

                    // Remove this sandbox's common services.
                    commonServiceRegistrations?.forEach { closeable ->
                        runIgnoringExceptions(closeable::close)
                    }

                    // And unload the (OSGi) sandbox group
                    sandboxCreationService.unloadSandboxGroup(sandboxGroupContext.sandboxGroup)
                }
                newContext
            }!!
        }
    }

    private fun registerCommonServices(vnc: VirtualNodeContext, bundles: Iterable<Bundle>): List<AutoCloseable>? {
        val bundleContext = bundles.firstOrNull()?.bundleContext ?: return null
        return (fetchCommonServices(vnc, bundles) + fetchSystemServices(vnc)).mapNotNull { requirement ->
            registerCommonServiceFor(requirement.first, requirement.second, bundleContext)
        }
    }

    /**
     * Locate suitable "prototype-scope" OSGi services to instantiate inside
     * the sandbox. We assume that the OSGi isolation hooks protect us from
     * finding any pre-existing services inside the sandbox itself.
     */
    private fun fetchCommonServices(vnc: VirtualNodeContext, bundles: Iterable<Bundle>): List<ServiceDefinition> {
        // Access control context for the sandbox's "main" bundles.
        // All "main" bundles are assumed to have equal access rights.
        val accessControlContext = bundles.first().adapt(AccessControlContext::class.java)
        val serviceFilter = vnc.serviceFilter?.let { filter -> "(&$SANDBOX_FACTORY_FILTER$filter)" } ?: SANDBOX_FACTORY_FILTER
        val serviceMarkerTypeName = vnc.serviceMarkerType.name
        return bundleContext.getServiceReferences(vnc.serviceMarkerType, serviceFilter).mapNotNull { serviceRef ->
            try {
                @Suppress("unchecked_cast")
                (serviceRef.getProperty(OBJECTCLASS) as? Array<String> ?: emptyArray())
                    .filterNot(serviceMarkerTypeName::equals)
                    .filter { checkServicePermission(accessControlContext, it) }
                    .mapNotNullTo(ArrayList(), bundles::loadCommonService)
                    .takeIf(List<*>::isNotEmpty)
                    ?.let { injectables ->
                        // Every service object must implement the service
                        // marker type and at least one other type too.
                        logger.debug { "Fetching common service: $serviceRef holding id ${vnc.holdingIdentity}" }
                        injectables += vnc.serviceMarkerType
                        bundleContext.getServiceObjects(serviceRef)?.let { serviceObj ->
                            serviceObj to injectables
                        }
                    }
            } catch (e: Exception) {
                logger.warn("Failed to identify injectable services from $serviceRef", e)
                null
            }
        }
    }

    private fun fetchSystemServices(vnc: VirtualNodeContext): List<ServiceDefinition> {
        val serviceFilter = vnc.serviceFilter?.let { filter -> "(&$SYSTEM_FACTORY_FILTER$filter)" } ?: SYSTEM_FACTORY_FILTER
        val serviceMarkerTypeName = vnc.serviceMarkerType.name
        return bundleContext.getServiceReferences(vnc.serviceMarkerType, serviceFilter).mapNotNull { serviceRef ->
            try {
                @Suppress("unchecked_cast")
                (serviceRef.getProperty(OBJECTCLASS) as? Array<String> ?: emptyArray())
                    .filterNot(serviceMarkerTypeName::equals)
                    .mapNotNullTo(ArrayList(), singleton(serviceRef.bundle)::loadCommonService)
                    .takeIf(List<*>::isNotEmpty)
                    ?.let { injectables ->
                        // Every service object must implement the service
                        // marker type and at least one other type too.
                        logger.debug { "Fetching system service: $serviceRef holding id ${vnc.holdingIdentity}" }
                        injectables += vnc.serviceMarkerType
                        bundleContext.getServiceObjects(serviceRef)?.let { serviceObj ->
                            serviceObj to injectables
                        }
                    }
            } catch (e: Exception) {
                logger.warn("Failed to identify injectable system services from $serviceRef", e)
                null
            }
        }
    }

    private fun registerCommonServiceFor(
        serviceFactory: ServiceObjects<out Any>,
        serviceClasses: Iterable<Class<*>>,
        bundleContext: BundleContext
    ): AutoCloseable? {
        val serviceObj = try {
            serviceFactory.service ?: return null
        } catch (e: Exception) {
            logger.warn("Service ${serviceFactory.serviceReference} is not available.", e)
            throw SandboxException("Service ${serviceFactory.serviceReference} is unavailable", e)
        }
        return try {
            val serviceRegistration = bundleContext.registerService(
                serviceClasses.mapTo(LinkedHashSet(), Class<*>::getName).toTypedArray(),
                serviceObj,
                sandboxServiceProperties
            )
            logger.info("Registered sandbox service [{}] for bundle [{}][{}]",
                serviceClasses.joinToString(transform = Class<*>::getName),
                bundleContext.bundle.symbolicName,
                bundleContext.bundle.bundleId
            )
            CommonServiceRegistration(serviceFactory, serviceObj, serviceRegistration)
        } catch (e: Exception) {
            logger.warn("Cannot create sandbox service ${serviceObj::class.java.name}", e)
            @Suppress("unchecked_cast")
            (serviceFactory as ServiceObjects<Any>).ungetService(serviceObj)
            null
        }
    }

    override fun registerMetadataServices(
        sandboxGroupContext: SandboxGroupContext,
        serviceNames: (CpkMetadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean,
        serviceMarkerType: Class<*>
    ): AutoCloseable {
        val groupMetadata = sandboxGroupContext.sandboxGroup.metadata
        val services = groupMetadata.flatMap { (mainBundle, cpkMetadata) ->
            // Fetch metadata classes provided by each CPK main bundle.
            serviceNames(cpkMetadata).mapNotNull { serviceName ->
                mainBundle.loadMetadataService(serviceName, isMetadataService)
            }
        }

        // Register each metadata service as an OSGi service for its host main bundle.
        val (instances, registrations) = registerMetadataServices(serviceMarkerType.name, services)
        (sandboxGroupContext as? MutableSandboxGroupContext)?.putObjectByKey(serviceMarkerType.name, instances)
        return AutoCloseable {
            registrations.forEach { registration ->
                runIgnoringExceptions(registration::close)
            }
        }
    }

    private fun registerMetadataServices(
        serviceMarkerTypeName: String,
        services: Iterable<Pair<Class<*>, Bundle>>
    ): Pair<Set<Any>, Deque<AutoCloseable>> {
        // These are the steps needed to unregister these services afterwards.
        // We will accumulate these clean-up steps as we go...
        val allCloseables = LinkedList<AutoCloseable>()

        // Create an instance of each service type, and register it as an OSGi service.
        return services.mapNotNullTo(LinkedHashSet()) { (serviceClass, serviceBundle) ->
            try {
                val serviceInterfaces = mutableSetOf(serviceMarkerTypeName, serviceClass.name)
                val serviceObj = serviceClass.getConstructor().newInstance()

                // Register this service object with the OSGi framework.
                val registration = serviceBundle.bundleContext.registerService(
                    serviceInterfaces.toTypedArray(),
                    serviceObj,
                    sandboxServiceProperties
                )

                logger.info("Registered Metadata Service [{}] for bundle [{}][{}]",
                    serviceInterfaces.joinToString(), serviceBundle.symbolicName, serviceBundle.bundleId)

                // Add an AutoCloseable that can unregister this service.
                allCloseables.addFirst(AutoCloseable(registration::unregister))

                // Return this service instance.
                serviceObj
            } catch (e: Exception) {
                logger.warn("Cannot create service ${serviceClass.name}", e)
                null
            }
        } to allCloseables
    }

    override fun registerCustomCryptography(sandboxGroupContext: SandboxGroupContext): AutoCloseable {
        return registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.digestAlgorithmFactories },
            serviceMarkerType = DigestAlgorithmFactory::class.java
        )
    }

    override fun hasCpks(cpkChecksums: Set<SecureHash>): Boolean {
        val missingCpks = cpkChecksums.filter {
            cpkReadService.get(it) == null
        }

        if(logger.isInfoEnabled && missingCpks.isNotEmpty()) {
            logger.info("CPK(s) not (yet) found in cache: $missingCpks")
        }

        return missingCpks.isEmpty()
    }

    override fun close() {
        cache.close()
    }

    private class CommonServiceRegistration(
        private val serviceFactory: ServiceObjects<out Any>,
        private val serviceObj: Any,
        private val serviceRegistration: ServiceRegistration<*>
    ) : AutoCloseable {
        override fun close() {
            runIgnoringExceptions(serviceRegistration::unregister)
            @Suppress("unchecked_cast")
            runIgnoringExceptions { (serviceFactory as ServiceObjects<Any>).ungetService(serviceObj) }
        }
    }

    /**
     * Check whether this [accessControlContext] is allowed to GET service [serviceType].
     */
    private fun checkServicePermission(accessControlContext: AccessControlContext, serviceType: String): Boolean {
        val sm = System.getSecurityManager()
        if (sm != null) {
            try {
                sm.checkPermission(ServicePermission(serviceType, GET), accessControlContext)
            } catch (ace: AccessControlException) {
                logger.error("This service failed GET permission check: $serviceType")
                return false
            }
        }
        return true
    }
}

/**
 * Try to load an interface for a service in the core platform.
 * Returns `null` if none of these [Bundle]s has a wiring for
 * that service interface.
 */
private fun Iterable<Bundle>.loadCommonService(serviceClassName: String): Class<*>? {
    forEach { bundle ->
        try {
            return bundle.loadClass(serviceClassName).takeIf(Class<*>::isInterface)
        } catch (_: ClassNotFoundException) {
        }
    }
    return null
}

/**
 * Locate the metadata service implementation called [serviceClassName] within this
 * [Bundle].
 */
private fun Bundle.loadMetadataService(serviceClassName: String, isMetadataService: (Class<*>) -> Boolean): Pair<Class<*>, Bundle>? {
    try {
        val serviceClass = loadClass(serviceClassName)
        if (isMetadataService(serviceClass)) {
            val serviceBundle = FrameworkUtil.getBundle(serviceClass)
            // Check that this is the correct bundle, rather
            // than being one that it only has a wiring for.
            if (serviceBundle === this) {
                return serviceClass to serviceBundle
            }
        }
    } catch (_: ClassNotFoundException) {
    }
    return null
}
