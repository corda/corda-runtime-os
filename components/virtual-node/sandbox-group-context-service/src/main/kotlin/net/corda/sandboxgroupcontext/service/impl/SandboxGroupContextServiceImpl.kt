@file:JvmName("SandboxGroupContextServiceUtils")
package net.corda.sandboxgroupcontext.service.impl

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.RequireCordaSystem
import net.corda.sandbox.RequireCordaSystem.CORDA_SYSTEM_NAMESPACE
import net.corda.sandbox.RequireSandboxHooks
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.CORDA_SYSTEM_FILTER
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxCrypto
import net.corda.sandboxgroupcontext.SANDBOX_SINGLETONS
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.CacheControl
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.sandboxgroupcontext.service.EvictionListener
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceObjects
import org.osgi.framework.ServicePermission
import org.osgi.framework.ServicePermission.GET
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.ComponentConstants.COMPONENT_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Collections.singleton
import java.util.Collections.unmodifiableSet
import java.util.Deque
import java.util.Hashtable
import java.util.LinkedList
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias SatisfiedServiceReferences = Map<String, SortedMap<ServiceReference<*>, Any>>

/**
 * This is the underlying implementation of the [SandboxGroupContextService]
 *
 * Use this service via the mutable and immutable interfaces to create a "virtual node",
 * and retrieve the same instance "later".
 *
 * This is a per-process service, but it must return the "same instance" for a given [VirtualNodeContext]
 * in EVERY process.
 */
@Suppress("TooManyFunctions")
@Component(service = [ SandboxGroupContextService::class, CacheEviction::class ])
@RequireSandboxCrypto
@RequireSandboxHooks
@RequireCordaSystem
class SandboxGroupContextServiceImpl @Activate constructor(
    @Reference
    private val sandboxCreationService: SandboxCreationService,
    @Reference
    private val cpkReadService: CpkReadService,
    @Reference
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext
) : SandboxGroupContextService, CacheControl {
    private companion object {
        private const val SANDBOX_FACTORY_FILTER = "(&($SERVICE_SCOPE=$SCOPE_PROTOTYPE)($COMPONENT_NAME=*)(!$CORDA_SANDBOX_FILTER))"
        private const val CORDA_UNINJECTABLE_FILTER = "(corda.uninjectable=*)"
        private const val CORDA_MARKER_ONLY_FILTER = "(corda.marker.only=*)"

        private val MARKER_INTERFACES: Set<String> = unmodifiableSet(
            SandboxGroupType.values()
                .mapTo(mutableSetOf(), SandboxGroupType::serviceMarkerType)
                .mapTo(mutableSetOf(), Class<*>::getName)
        )
        private val uninjectableFilter = FrameworkUtil.createFilter(CORDA_UNINJECTABLE_FILTER)
        private val markerOnlyFilter = FrameworkUtil.createFilter(CORDA_MARKER_ONLY_FILTER)
        private val systemFilter = FrameworkUtil.createFilter(CORDA_SYSTEM_FILTER)
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun ServiceReference<*>.copyPropertiesForSandbox(sandboxId: UUID): Hashtable<String, Any?> {
            return Hashtable<String, Any?>().also { props ->
                props[CORDA_SANDBOX] = sandboxId
                propertyKeys.forEach { key ->
                    props[key] = getProperty(key)
                }
            }
        }

        private fun <R> runIgnoringExceptions(action: () -> R) {
            try {
                action()
            } catch (e: Exception) {
                logger.debug("Ignoring exception", e)
            }
        }
    }

    private val cache = SandboxGroupContextCacheImpl(0)
    private val lock = ReentrantLock()

    override fun resizeCache(type: SandboxGroupType, capacity: Long) = lock.withLock {
        if (capacity != cache.capacities[type]) {
            logger.info("Changing Sandbox cache capacity for type {} from {} to {}", type, cache.capacities[type], capacity)
            cache.resize(type, capacity)
        }
    }

    override fun flushCache(): CompletableFuture<*> = lock.withLock {
        cache.flush()
    }

    @Throws(InterruptedException::class)
    override fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean = lock.withLock {
        return cache.waitFor(completion, duration)
    }

    override fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? = lock.withLock {
        cache.remove(virtualNodeContext)
    }

    override fun addEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean = lock.withLock {
        cache.addEvictionListener(type, listener)
    }

    override fun removeEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean = lock.withLock{
        cache.removeEvictionListener(type, listener)
    }

    override fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext = lock.withLock {
        cache.get(virtualNodeContext) { vnc ->
            val sandboxTimer = CordaMetrics.Metric.SandboxCreateTime.builder()
                .forVirtualNode(vnc.holdingIdentity.shortHash.value)
                .withTag(CordaMetrics.Tag.SandboxGroupType, vnc.sandboxGroupType.name)
                .build()
            sandboxTimer.recordCallable<CloseableSandboxGroupContext> {
                val cpks = vnc.cpkFileChecksums.mapNotNull(cpkReadService::get)
                if (cpks.size != vnc.cpkFileChecksums.size) {
                    val receivedIdentifiers = cpks.map { it.metadata.cpkId }
                    val missing = setOf(vnc.cpkFileChecksums) - setOf(receivedIdentifiers)
                    logger.error("Not all CPKs could be retrieved for this virtual node context ({})\r\n" +
                        "- Wanted all of: {}\r\n" +
                        "- Returned: {}\r\n" +
                        "- Missing: {}",
                        vnc, vnc.cpkFileChecksums, receivedIdentifiers, missing
                    )
                    throw CordaRuntimeException("Not all CPKs could be retrieved for this virtual node context ($vnc)")
                }
                if (cpks.isEmpty()) {
                    throw CordaRuntimeException("No CPKs in this virtual node context. " +
                            "State and contract classes must be defined inside a contract CPK. ($vnc)")
                }

                val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks, vnc.sandboxGroupType.name)

                // Default implementation doesn't do anything on close()`
                val sandboxGroupContext = SandboxGroupContextImpl(vnc, sandboxGroup)

                // Register common OSGi services for use within this sandbox.
                val commonServiceRegistrations = registerCommonServices(sandboxGroupContext)?.let {
                    sandboxGroupContext.putObjectByKey(SANDBOX_SINGLETONS, it.first)
                    it.second
                }

                // Run the caller's initializer.
                val initializerAutoCloseable =
                    initializer.initializeSandboxGroupContext(vnc.holdingIdentity, sandboxGroupContext)

                logger.debug("Created {} sandbox {} for holding identity={}",
                    vnc.sandboxGroupType, sandboxGroup.id, vnc.holdingIdentity)

                // Wrapped SandboxGroupContext, specifically to set closeable and forward on all other calls.

                // Calling close also removes us from the contexts map and unloads the [SandboxGroup].
                CloseableSandboxGroupContextImpl(sandboxGroupContext) {
                    // These objects might still be in a sandbox, so close them whilst the sandbox is still valid.
                    runIgnoringExceptions(initializerAutoCloseable::close)

                    // Remove this sandbox's common services.
                    commonServiceRegistrations?.forEach { closeable ->
                        runIgnoringExceptions(closeable::close)
                    }

                    // And unload the (OSGi) sandbox group
                    sandboxCreationService.unloadSandboxGroup(sandboxGroupContext.sandboxGroup)
                }
            }!!
        }
    }

    private fun registerCommonServices(sandboxGroupContext: SandboxGroupContext): Pair<Set<*>, Collection<AutoCloseable>>? {
        val sandboxGroup = sandboxGroupContext.sandboxGroup
        val bundles = sandboxGroup.metadata.keys
        val targetContext = bundles.firstOrNull()?.bundleContext ?: return null
        return createSandboxServiceContext(sandboxGroup.id, sandboxGroupContext.virtualNodeContext, bundles)
            .registerInjectables(targetContext)
    }

    /**
     * Determine all bundles which the OSGi framework has wired to
     * this bundle's "corda.system" requirement, which means they
     * must all advertise a compatible "corda.system" capability.
     */
    private fun getCordaSystemBundles(sandboxGroupType: SandboxGroupType): Set<Bundle> {
        return bundleContext.bundle.adapt(BundleWiring::class.java).getRequiredWires(CORDA_SYSTEM_NAMESPACE)
            ?.filter { it.capability.attributes[CORDA_SYSTEM_NAMESPACE] == sandboxGroupType.toString() }
            ?.mapTo(linkedSetOf()) { wire ->
                wire.provider.bundle
            } ?: emptySet()
    }

    /**
     * Locate suitable "prototype-scope" OSGi services to instantiate inside
     * the sandbox. We assume that the OSGi isolation hooks protect us from
     * finding any pre-existing services inside the sandbox itself.
     *
     * Identify which of these services should be registered with the OSGi framework
     * as "injectable" services, i.e. candidates for `@CordaInject`.
     */
    @Suppress("ComplexMethod")
    private fun createSandboxServiceContext(
        sandboxId: UUID,
        vnc: VirtualNodeContext,
        bundles: Iterable<Bundle>
    ): SandboxServiceContext {
        val injectables = mutableMapOf<ServiceReference<*>, ServiceDefinition>()
        val serviceIndex = mutableMapOf<String, MutableSet<ServiceReference<*>>>()

        // Access control context for the sandbox's "main" bundles.
        // All "main" bundles are assumed to have equal access rights.
        @Suppress("deprecation", "removal")
        val accessControlContext = bundles.first().adapt(java.security.AccessControlContext::class.java)

        val sandboxGroupType = vnc.sandboxGroupType
        val sandboxBundles = bundles + getCordaSystemBundles(sandboxGroupType)

        val serviceMarkerType = sandboxGroupType.serviceMarkerType
        val sandboxFilter = vnc.serviceFilter?.let { filter -> "(&$SANDBOX_FACTORY_FILTER$filter)" } ?: SANDBOX_FACTORY_FILTER
        bundleContext.getServiceReferences(serviceMarkerType, sandboxFilter).forEach { serviceRef ->
            try {
                serviceRef.serviceClassNames
                    .filterNot(MARKER_INTERFACES::contains)
                    .onEach { serviceType ->
                        serviceIndex.computeIfAbsent(serviceType) { HashSet() }.add(serviceRef)
                    }.takeIfElse(emptyList()) {
                        // Services are only "injectable" if this sandbox type supports injection.
                        sandboxGroupType.hasInjection
                    }.mapNotNullTo(ArrayList()) { serviceType ->
                        if (systemFilter.match(serviceRef)) {
                            // We always load interfaces for system services.
                            serviceRef.loadCommonService(serviceType)
                        } else if (!uninjectableFilter.match(serviceRef) && accessControlContext.checkServicePermission(serviceType)) {
                            // Only accept those service types for which this sandbox also has a bundle wiring.
                            sandboxBundles.loadCommonService(serviceType)
                        } else {
                            logger.debug("Holding ID {} denied GET permission for {}", vnc.holdingIdentity, serviceType)
                            null
                        }
                    }.takeIf { serviceTypes ->
                        // Every service object must implement the service marker type
                        // and at least one other type too, unless declared with the
                        // corda.marker.only property.
                        serviceTypes.isNotEmpty() || markerOnlyFilter.match(serviceRef)
                    }?.also { injectableTypes ->
                        logger.debug("Identified injectable service {}, holding ID={}", serviceRef, vnc.holdingIdentity)
                        injectableTypes += serviceMarkerType

                        // We filtered on services having a component name, so we
                        // should be guaranteed to find its component description.
                        serviceComponentRuntime.getComponentDescriptionDTO(serviceRef)?.also { description ->
                            injectables[serviceRef] = ServiceDefinition(injectableTypes, description, vnc.serviceFilter)
                        }
                    }
            } catch (e: Exception) {
                logger.warn("Failed to identify injectable services from $serviceRef", e)
            }
        }

        // Include the service marker interfaces in our "universe" of sandbox services,
        // but without any ServiceReference<*> objects. This effectively prevents the
        // sandbox from injecting anything against any marker interface reference,
        // which sandbox components are not supposed to have anyway.
        val emptyImmutableSet = java.util.Collections.emptySet<ServiceReference<*>>()
        MARKER_INTERFACES.forEach { markerName ->
            serviceIndex[markerName] = emptyImmutableSet
        }

        return SandboxServiceContext(
            sandboxId = sandboxId,
            serviceFilter = vnc.serviceFilter,
            sourceContext = bundleContext,
            serviceComponentRuntime,
            serviceIndex,
            injectables
        )
    }

    override fun registerMetadataServices(
        sandboxGroupContext: SandboxGroupContext,
        serviceNames: (CpkMetadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean,
        serviceMarkerType: Class<*>
    ): AutoCloseable = lock.withLock {
        val group = sandboxGroupContext.sandboxGroup
        val services = group.metadata.flatMap { (mainBundle, cpkMetadata) ->
            // Fetch metadata classes provided by each CPK main bundle.
            serviceNames(cpkMetadata).mapNotNull { serviceName ->
                mainBundle.loadMetadataService(serviceName, isMetadataService)
            }
        }

        // Register each metadata service as an OSGi service for its host main bundle.
        val (instances, registrations) = registerMetadataServices(group.id, serviceMarkerType.name, services)
        (sandboxGroupContext as? MutableSandboxGroupContext)?.putObjectByKey(serviceMarkerType.name, instances)
        return AutoCloseable {
            registrations.forEach { registration ->
                runIgnoringExceptions(registration::close)
            }
        }
    }

    private fun registerMetadataServices(
        sandboxId: UUID,
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
                val serviceObj = serviceClass.getConstructor().let { ctor ->
                    // Allow instantiation of classes which are non-public.
                    ctor.isAccessible = true
                    ctor.newInstance()
                }

                // Register this service object with the OSGi framework.
                val registration = serviceBundle.bundleContext.registerService(
                    serviceInterfaces.toTypedArray(),
                    serviceObj,
                    Hashtable<String, Any?>().also { props ->
                        props[CORDA_SANDBOX] = sandboxId
                    }
                )

                if (logger.isDebugEnabled) {
                    logger.debug(
                        "Registered Metadata Service [{}] for bundle [{}][{}]",
                        serviceInterfaces.joinToString(), serviceBundle.symbolicName, serviceBundle.bundleId
                    )
                }

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

    override fun acceptCustomMetadata(sandboxGroupContext: MutableSandboxGroupContext) {
        lock.withLock {
            sandboxGroupContext.getObjectByKey<Iterable<Any>>(SANDBOX_SINGLETONS)
                ?.filterIsInstance<CustomMetadataConsumer>()
                ?.forEach { customMetadataConsumer ->
                    customMetadataConsumer.accept(sandboxGroupContext)
                }
        }
    }

    override fun hasCpks(cpkChecksums: Set<SecureHash>): Boolean = lock.withLock {
        val missingCpks = cpkChecksums.filter {
            cpkReadService.get(it) == null
        }

        if (logger.isInfoEnabled && missingCpks.isNotEmpty()) {
            logger.info("CPK(s) not (yet) found in cache: {}", missingCpks)
        }

        missingCpks.isEmpty()
    }

    @Deactivate
    override fun close() = lock.withLock {
        cache.close()
    }

    /**
     * An [AutoCloseable] associated with an injectable service, i.e. one which
     * has also been registered with the OSGi framework as a singleton. Closing
     * this object will unregister the service and release all the references
     * to its dependencies.
     */
    private class InjectableServiceRegistration(
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
     * An [AutoCloseable] associated with a non-injectable service. This service
     * has not actually been registered with the OSGi framework, but may still
     * hold references to other OSGi services which should be released at the end.
     */
    private class NonInjectableServiceRegistration(
        private val serviceFactory: ServiceObjects<out Any>,
        private val serviceObj: Any
    ) : AutoCloseable {
        override fun close() {
            @Suppress("unchecked_cast")
            runIgnoringExceptions { (serviceFactory as ServiceObjects<Any>).ungetService(serviceObj) }
        }
    }

    /**
     * We need to create instances of every injectable service that we have identified.
     * These injectables will all be created using "constructor injection", and will
     * likely require several other - possible non-injectable - services as parameters
     * too. The algorithm is therefore a bit tricky.
     *
     * @property sandboxId unique identifier for our sandbox instance.
     * @property sourceContext the [BundleContext] of [SandboxGroupContextComponentImpl].
     * @property serviceComponentRuntime a reference to OSGi's [ServiceComponentRuntime].
     * @property injectables the services we know we still need to create.
     * @property serviceIndex our "universe" of potential sandbox services, of which
     * both [injectables] and [nonInjectables] are subsets.
     *
     * Every service we successfully create is added into [serviceRegistry]. If we fail
     * to create a service then we mark it as broken, and do not try to create it again.
     *
     * We never add new entries into [injectables]. If we discover that a service requires
     * another service that exists in [serviceIndex], but doesn't already exist in either
     * [injectables], [nonInjectables] or [serviceRegistry] then we add that service into
     * [nonInjectables].
     *
     * We can create a new service as soon all its dependent sandbox services have been
     * created, i.e. all its references from [serviceIndex] exist in [serviceRegistry].
     * Any other services it requires are fetched from the OSGi framework using
     * [BundleContext.getService].
     *
     * The goal is to keep creating new services until [injectables] becomes empty,
     * by which point [nonInjectables] should also be empty. However, we will also
     * abort this process if we ever iterate over [injectables] without achieving
     * anything new.
     */
    @Suppress("LongParameterList")
    private class SandboxServiceContext(
        private val sandboxId: UUID,
        private val serviceFilter: String?,
        private val sourceContext: BundleContext,
        private val serviceComponentRuntime: ServiceComponentRuntime,
        private val serviceIndex: Map<String, Set<ServiceReference<*>>>,
        private val injectables: MutableMap<ServiceReference<*>, ServiceDefinition>
    ) {
        private val nonInjectables = mutableMapOf<ServiceReference<*>, ServiceDefinition>()
        private val serviceRegistry = mutableMapOf<ServiceReference<*>, Any>()

        init {
            // Allow injectables to compute their own sandbox service references.
            // We must do this before we can invoke registerInjectables().
            for (injectable in injectables.values) {
                injectable.withServiceReferences(serviceIndex)
            }
        }

        /**
         * Create the requested injectable services, along with any non-injectable services
         * that they may also require. Register the injectable services as singletons for
         * [targetContext], and return both the services and whatever [AutoCloseable]
         * clean-up actions are required to dispose of them all nicely afterwards.
         * @param targetContext a [BundleContext] for one of the sandbox bundles.
         */
        fun registerInjectables(targetContext: BundleContext): Pair<Set<*>, Collection<AutoCloseable>> {
            val closeables = LinkedList<AutoCloseable>()
            return try {
                // Register all injectables which don't reference other prototype services.
                // We can ask the OSGi framework to create these.
                registerSimpleInjectables(targetContext, closeables)

                // Register the remaining injectables, which we must create ourselves.
                while (injectables.isNotEmpty()) {
                    // Create as many non-injectable services as we can before
                    // trying to create any more injectable ones. These may
                    // require other non-injectable services themselves.
                    createNonInjectables(closeables)

                    if (!registerComplexInjectables(targetContext, closeables)) {
                        logger.warn("Failed to create sandbox injectables: {}",
                            injectables.values
                                .flatMapTo(LinkedHashSet(), ServiceDefinition::serviceClassNames)
                                .joinToString()
                        )
                        break
                    }
                }

                // This shouldn't log anything unless we also failed to create some injectables.
                if (nonInjectables.isNotEmpty()) {
                    logger.warn("Failed to create sandbox non-injectables: {}", nonInjectables.values.joinToString())
                }

                Pair(unmodifiableSet(serviceRegistry.values.toSet()), closeables)
            } catch (e: Exception) {
                closeables.forEach(::closeSafely)
                throw e
            }
        }

        /**
         * Iterate over [injectables], registering those which have no requirements to satisfy.
         * We will also identify any [nonInjectables] which the [injectables] require.
         */
        private fun registerSimpleInjectables(targetContext: BundleContext, closeables: Deque<AutoCloseable>) {
            val totalRequirements = mutableSetOf<ServiceReference<*>>()
            val iter = injectables.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val injectable = entry.value
                val sandboxRequirements = injectable.sandboxReferences

                if (!injectable.isByConstructor) {
                    logger.warn("{} must only use constructor injection - IGNORED", injectable)
                    injectable.asBroken()
                } else if (sandboxRequirements.isNotEmpty()) {
                    // Collect this service's requirements so that
                    // we can examine all requirements afterwards.
                    // We will not process this service further.
                    sandboxRequirements.values.forEach(totalRequirements::addAll)
                } else if (serviceFilter == null) {
                    // This service doesn't use any of our prototypes, and we don't
                    // need to filter the set of available services, which means that
                    // the OSGi framework can safely create our new service instance.
                    sourceContext.getServiceObjects(entry.key)?.also { serviceObj ->
                        registerInjectableSandboxService(
                            serviceObj,
                            injectable.serviceClassNames,
                            targetContext
                        )?.also { svc ->
                            closeables.addFirst(svc)
                            iter.remove()
                        } ?: run(injectable::asBroken)
                    }
                }
            }

            // Discover any new and unsatisfied service references, which we will consider to be non-injectable.
            getUnknownServicesFrom(totalRequirements).forEach { nonInjectable ->
                addNonInjectable(nonInjectable, closeables)
            }
        }

        /**
         * Iterate over [injectables], registering those whose requirements can be satisfied.
         * @return true if at least one new injectable service was registered.
         */
        private fun registerComplexInjectables(targetContext: BundleContext, closeables: Deque<AutoCloseable>): Boolean {
            var modified = false
            val iter = injectables.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val injectable = entry.value
                if (injectable.isBroken) {
                    continue
                }

                val sandboxRequirements = injectable.sandboxReferences
                val satisfied = satisfy(sandboxRequirements)
                if (satisfied != null) {
                    registerInjectableSandboxService(
                        SandboxServiceObjects(entry.key, injectable, satisfied),
                        injectable.serviceClassNames,
                        targetContext
                    )?.also { svc ->
                        closeables.addFirst(svc)
                        modified = true
                        iter.remove()
                    }
                }
            }
            return modified
        }

        /**
         * Creates a service object using [serviceFactory], and then registers
         * it as a singleton service for [targetContext]. The service object
         * is then added to [serviceRegistry] as a "satisfied" reference.
         */
        private fun registerInjectableSandboxService(
            serviceFactory: ServiceObjects<out Any>,
            serviceClassNames: Set<String>,
            targetContext: BundleContext
        ): AutoCloseable? {
            val serviceObj = try {
                serviceFactory.service ?: return null
            } catch (e: Exception) {
                throw SandboxException("Service ${serviceFactory.serviceReference} is unavailable", e)
            }
            return try {
                val serviceRegistration = targetContext.registerService(
                    serviceClassNames.toTypedArray(),
                    serviceObj,
                    serviceFactory.serviceReference.copyPropertiesForSandbox(sandboxId)
                )
                if (logger.isDebugEnabled) {
                    logger.debug("Registered sandbox service {}[{}] for bundle [{}][{}]",
                        serviceObj::class.java.simpleName,
                        serviceClassNames.joinToString(),
                        targetContext.bundle.symbolicName,
                        targetContext.bundle.bundleId
                    )
                }
                serviceRegistry[serviceFactory.serviceReference] = serviceObj
                InjectableServiceRegistration(serviceFactory, serviceObj, serviceRegistration)
            } catch (e: Exception) {
                logger.warn("Cannot create sandbox service ${serviceObj::class.java.name}", e)
                @Suppress("unchecked_cast")
                (serviceFactory as ServiceObjects<Any>).ungetService(serviceObj)
                null
            }
        }

        /**
         * Repeatedly iterate over [nonInjectables], registering those whose requirements can be satisfied.
         * Continue until we can no longer register existing non-injectable services or discover new ones.
         */
        private tailrec fun createNonInjectables(closeables: Deque<AutoCloseable>) {
            var modified = false
            val totalRequirements = mutableSetOf<ServiceReference<*>>()

            // Create any non-injectable services whose requirements are already satisfied.
            val iter = nonInjectables.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val nonInjectable = entry.value
                if (nonInjectable.isBroken) {
                    continue
                }

                val requirements = nonInjectable.sandboxReferences
                val satisfied = satisfy(requirements)
                if (satisfied != null) {
                    val serviceFactory = SandboxServiceObjects(entry.key, nonInjectable, satisfied)
                    registerNonInjectableSandboxService(serviceFactory)?.also { svc ->
                        closeables.addFirst(svc)
                        modified = true
                        iter.remove()
                    }
                } else {
                    requirements.values.forEach(totalRequirements::addAll)
                }
            }

            // Discover any new and unsatisfied service references, which we will also consider to be non-injectable.
            getUnknownServicesFrom(totalRequirements).forEach { ref ->
                if (addNonInjectable(ref, closeables)) {
                    modified = true
                }
            }

            // This is the "tail-recursive" step:
            // Determine whether our actions have allowed us to create/discover other non-injectables.
            if (modified) {
                createNonInjectables(closeables)
            }
        }

        private fun addNonInjectable(serviceRef: ServiceReference<*>, closeables: Deque<AutoCloseable>): Boolean {
            var modified = false
            serviceComponentRuntime.getComponentDescriptionDTO(serviceRef)?.let { description ->
                val nonInjectable = ServiceDefinition(description, serviceFilter).withServiceReferences(serviceIndex)
                if (!nonInjectable.isByConstructor) {
                    logger.warn("{} must only use constructor injection - IGNORED", nonInjectable)
                    nonInjectables[serviceRef] = nonInjectable.asBroken()
                    null
                } else if (nonInjectable.sandboxReferences.isEmpty() && serviceFilter == null) {
                    // This service doesn't use any of our prototypes, and we don't
                    // need to filter the set of available services, which means that
                    // the OSGi framework can safely create our new service instance.
                    sourceContext.getServiceObjects(serviceRef)
                        ?.let(::registerNonInjectableSandboxService)
                        ?: run {
                            nonInjectables[serviceRef] = nonInjectable.asBroken()
                            null
                        }
                } else {
                    logger.debug("Discovered non-injectable sandbox service {}", serviceRef)
                    nonInjectables[serviceRef] = nonInjectable
                    modified = true
                    null
                }
            }?.also { closeable ->
                closeables.addFirst(closeable)
                modified = true
            }
            return modified
        }

        /**
         * Creates a service object using [serviceFactory], and then adds it
         * to [serviceRegistry] as a "satisfied" reference.
         */
        private fun registerNonInjectableSandboxService(serviceFactory: ServiceObjects<out Any>): AutoCloseable? {
            val serviceRef = serviceFactory.serviceReference
            return try {
                serviceFactory.service
            } catch (e: Exception) {
                throw SandboxException("Service $serviceRef is unavailable", e)
            }?.let { serviceObj ->
                logger.debug("Created non-injectable sandbox service: {}", serviceObj::class.java.name)
                serviceRegistry[serviceRef] = serviceObj
                NonInjectableServiceRegistration(serviceFactory, serviceObj)
            }
        }

        /**
         * @return those [services] which are neither unsatisfied [injectables], unsatisfied
         * [nonInjectables], nor satisfied services from [serviceRegistry].
         */
        private fun getUnknownServicesFrom(services: Set<ServiceReference<*>>): Set<ServiceReference<*>> {
            return services - serviceRegistry.keys - injectables.keys - nonInjectables.keys
        }

        /**
         * Assemble a [SatisfiedServiceReferences] for the given [requirements],
         * based on the contents of [serviceRegistry].
         * @return [SatisfiedServiceReferences], or `null` if any [ServiceReference] we need is
         * still missing from [serviceRegistry].
         */
        private fun satisfy(requirements: Map<String, Set<ServiceReference<*>>>): SatisfiedServiceReferences? {
            return buildMap {
                requirements.forEach { (svcType, svcRefs) ->
                    svcRefs.forEach { svcRef ->
                        // If we've already created the service for this ServiceReference
                        // then add it to our satisfied requirements. Otherwise, we cannot
                        // possibly have everything we need yet and so should ABORT NOW!
                        serviceRegistry[svcRef]?.also { obj ->
                            // Sort the ServiceReference objects by decreasing rank.
                            (computeIfAbsent(svcType) { TreeMap(reverseOrder()) })[svcRef] = obj
                        } ?: return null
                    }
                }
            }
        }
    }
}

/**
 * Close an [AutoCloseable] while ignoring any exceptions.
 */
fun closeSafely(closeable: AutoCloseable) {
    try {
        closeable.close()
    } catch (_: Exception) {
    }
}

/**
 * Check whether this [AccessControlContext][java.security.AccessControlContext] is allowed to GET service [serviceType].
 */
@Suppress("deprecation", "removal")
private fun java.security.AccessControlContext.checkServicePermission(serviceType: String): Boolean {
    val sm = System.getSecurityManager()
    if (sm != null) {
        try {
            sm.checkPermission(ServicePermission(serviceType, GET), this)
        } catch (ace: java.security.AccessControlException) {
            return false
        }
    }
    return true
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
 * Try to load an interface for a service in the core platform.
 * Returns `null` if this [ServiceReference]'s [Bundle] has no
 * wiring for that service interface.
 */
private fun ServiceReference<*>.loadCommonService(serviceClassName: String): Class<*>? {
    return singleton(bundle).loadCommonService(serviceClassName)
}

/**
 * Locate the metadata service implementation called [serviceClassName] within this [Bundle].
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

@OptIn(ExperimentalContracts::class)
private inline fun <T> T.takeIfElse(otherwise: T, predicate: (T) -> Boolean): T {
    contract {
        callsInPlace(predicate, InvocationKind.EXACTLY_ONCE)
    }
    return if (predicate(this)) this else otherwise
}

private val ServiceReference<*>.serviceClassNames: Array<String>
    get() {
        @Suppress("unchecked_cast")
        return getProperty(OBJECTCLASS) as? Array<String> ?: emptyArray()
    }

private fun ServiceComponentRuntime.getComponentDescriptionDTO(serviceRef: ServiceReference<*>): ComponentDescriptionDTO? {
    return getComponentDescriptionDTO(serviceRef.bundle, serviceRef.getProperty(COMPONENT_NAME).toString())
}
