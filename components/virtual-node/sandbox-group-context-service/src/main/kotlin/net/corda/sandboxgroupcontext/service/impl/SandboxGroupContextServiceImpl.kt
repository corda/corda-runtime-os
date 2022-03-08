@file:JvmName("SandboxGroupContextServiceUtils")
package net.corda.sandboxgroupcontext.service.impl

import net.corda.install.InstallService
import net.corda.libs.packaging.CpkIdentifier
import net.corda.libs.packaging.CpkMetadata
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SCOPE_SINGLETON
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceObjects
import org.osgi.framework.ServiceRegistration
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO
import java.util.Hashtable
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

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
    private val installService: InstallService,
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext
) : SandboxGroupContextService, AutoCloseable {
    private companion object {
        private const val SANDBOX_FACTORY_FILTER = "(&($SERVICE_SCOPE=$SCOPE_PROTOTYPE)(!$CORDA_SANDBOX_FILTER))"

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

        private fun ComponentDescriptionDTO.toShortString(): String
            = "Component(class=$implementationClass, services=${serviceInterfaces.joinToString()}, scope=$scope, enabled=$defaultEnabled)"
    }

    private val contexts = ConcurrentHashMap<VirtualNodeContext, CloseableSandboxGroupContext>()

    fun remove(virtualNodeContext: VirtualNodeContext) {
        // close actually removes us from the map.
        contexts[virtualNodeContext]?.close()
    }

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext {
        return contexts[virtualNodeContext] ?: run {
            // returns a nullable from a future...
            val cpks = virtualNodeContext.cpkIdentifiers.mapNotNull { installService.get(it).get() }

            val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks, virtualNodeContext.sandboxGroupType.name)

            // Default implementation doesn't do anything on close()`
            val sandboxGroupContext = SandboxGroupContextImpl(virtualNodeContext, sandboxGroup)

            // Register common OSGi services for use within this sandbox.
            val commonServiceRegistrations = registerCommonServices(virtualNodeContext, sandboxGroup.metadata.keys)

            // Run the caller's initializer.
            val initializerAutoCloseable =
                initializer.initializeSandboxGroupContext(virtualNodeContext.holdingIdentity, sandboxGroupContext)

            // Wrapped SandboxGroupContext, specifically to set closeable and forward on all other calls.

            // Calling close also removes us from the contexts map and unloads the [SandboxGroup].
            val newContext = CloseableSandboxGroupContext(sandboxGroupContext, ::removeContextIfPresent) {
                // These objects might still be in a sandbox, so close them whilst the sandbox is still valid.
                initializerAutoCloseable.close()

                // Remove this sandbox's common services.
                commonServiceRegistrations?.forEach { closeable ->
                    runIgnoringExceptions(closeable::close)
                }

                // And unload the (OSGi) sandbox group
                sandboxCreationService.unloadSandboxGroup(sandboxGroupContext.sandboxGroup)
            }

            contexts.putIfAbsent(virtualNodeContext, newContext)?.also {
                // Someone has ninja'd another SandboxGroupContext into
                // the map while we were creating this one. Destroy the
                // one we just created as we don't need it any more.
                runIgnoringExceptions(newContext::close)
            } ?: newContext
        }
    }

    private fun removeContextIfPresent(context: CloseableSandboxGroupContext) {
        contexts.computeIfPresent(context.virtualNodeContext) { _, value ->
            if (value === context) {
                null
            } else {
                value
            }
        }
    }

    private fun registerCommonServices(vnc: VirtualNodeContext, bundles: Iterable<Bundle>): List<AutoCloseable>? {
        val bundleContext = bundles.firstOrNull()?.bundleContext ?: return null
        return fetchCommonServices(vnc, bundles).mapNotNull { requirement ->
            registerCommonServiceFor(requirement.first, requirement.second, bundleContext)
        }
    }

    /**
     * Locate suitable "prototype-scope" OSGi services to instantiate inside
     * the sandbox. We assume that the OSGi isolation hooks protect us from
     * finding any pre-existing services inside the sandbox itself.
     */
    private fun fetchCommonServices(vnc: VirtualNodeContext, bundles: Iterable<Bundle>): List<ServiceDefinition> {
        val serviceFilter = vnc.serviceFilter?.let { filter -> "(&$SANDBOX_FACTORY_FILTER$filter)" } ?: SANDBOX_FACTORY_FILTER
        val serviceMarkerTypeName = vnc.serviceMarkerType.name
        return bundleContext.getServiceReferences(vnc.serviceMarkerType, serviceFilter).mapNotNull { serviceRef ->
            try {
                @Suppress("unchecked_cast")
                (serviceRef.getProperty(OBJECTCLASS) as? Array<String> ?: emptyArray())
                    .filterNot(serviceMarkerTypeName::equals)
                    .mapNotNullTo(ArrayList(), bundles::loadCommonService)
                    .takeIf(List<*>::isNotEmpty)
                    ?.let { injectables ->
                        // Every service object must implement the service
                        // marker type and at least one other type too.
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
        val registrations = registerMetadataServices(serviceMarkerType.name, services)
        return AutoCloseable {
            registrations.forEach { registration ->
                runIgnoringExceptions(registration::close)
            }
        }
    }

    private fun registerMetadataServices(
        serviceMarkerTypeName: String,
        services: Iterable<Pair<Class<*>, Bundle>>
    ): List<AutoCloseable> {
        val extraCloseables = mutableListOf<AutoCloseable>()
        return services.mapNotNull { (serviceClass, serviceBundle) ->
            try {
                val serviceContext = serviceBundle.bundleContext
                val component = serviceComponentRuntime.getComponentDescriptionDTOs(serviceBundle).find { description ->
                    description.implementationClass == serviceClass.name
                }
                val serviceInterfaces = mutableSetOf(serviceMarkerTypeName)
                val serviceObj = if (component == null) {
                    serviceInterfaces += serviceClass.name
                    serviceClass.getConstructor().newInstance()
                } else if (component.defaultEnabled
                    && component.scope == SCOPE_SINGLETON
                    && component.implementationClass in component.serviceInterfaces) {
                    serviceInterfaces += component.serviceInterfaces
                    serviceContext.getServiceReference(component.implementationClass)?.let { reference ->
                        serviceContext.getService(reference)?.also {
                            // Ensure we can "unget" this service later.
                            extraCloseables.add(AutoCloseable { serviceContext.ungetService(reference) })
                        }
                    } ?: throw IllegalStateException("No ${component.toShortString()} active for bundle $serviceBundle")
                } else {
                    logger.warn("Ignoring misconfigured OSGi ${component.toShortString()} for service ${serviceClass.name}")
                    return@mapNotNull null
                }

                // Register this service object with the OSGi framework.
                val registration = serviceContext.registerService(
                    serviceInterfaces.toTypedArray(),
                    serviceObj,
                    sandboxServiceProperties
                )

                logger.info("Registered Metadata Service [{}] for bundle [{}][{}]",
                    serviceInterfaces.joinToString(), serviceBundle.symbolicName, serviceBundle.bundleId)

                // Return an AutoCloseable that can unregister this service.
                AutoCloseable(registration::unregister)
            } catch (e: Exception) {
                logger.warn("Cannot create service ${serviceClass.name}", e)
                null
            }
        } + extraCloseables
    }

    override fun registerCustomCryptography(sandboxGroupContext: SandboxGroupContext): AutoCloseable {
        return registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.digestAlgorithmFactories },
            serviceMarkerType = DigestAlgorithmFactory::class.java
        )
    }

    override fun hasCpks(cpkIdentifiers: Set<CpkIdentifier>) : Boolean {
        // This needs to be updated when the CPK service is introduced.
        return true
    }

    /**
     * [MutableSandboxGroupContext] / [SandboxGroupContext] wrapped so that we set [close] now that the user has
     * returned it as part of their [SandboxGroupContextInitializer]
     *
     * We return an instance of this object of type [SandboxGroupContext] to the user once [getOrCreate] is complete.
     */
    private class CloseableSandboxGroupContext(
        private val sandboxGroupContext: SandboxGroupContextImpl,
        private val removeContextIfPresent: Consumer<CloseableSandboxGroupContext>,
        private val closeable: AutoCloseable
    ) : MutableSandboxGroupContext, AutoCloseable {
        override fun <T : Any> put(key: String, value: T) =
            sandboxGroupContext.put(key, value)

        override val virtualNodeContext: VirtualNodeContext
            get() = sandboxGroupContext.virtualNodeContext

        override val sandboxGroup: SandboxGroup
            get() = sandboxGroupContext.sandboxGroup

        override fun <T : Any> get(key: String, valueType: Class<out T>): T? = sandboxGroupContext.get(key, valueType)

        override fun close() {
            runIgnoringExceptions { removeContextIfPresent.accept(this) }
            runIgnoringExceptions(closeable::close)
        }
    }

    override fun close() {
        contexts.values.forEach { closeable ->
            runIgnoringExceptions(closeable::close)
        }
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
