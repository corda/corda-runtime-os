@file:Suppress("MaxLineLength")
package net.corda.sandboxgroupcontext.service.impl

import java.lang.reflect.Constructor
import java.lang.reflect.Parameter
import java.util.Collections.unmodifiableMap
import java.util.Deque
import java.util.LinkedList
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.ServiceReference
import org.osgi.service.component.ComponentConstants.COMPONENT_NAME
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO
import org.osgi.service.component.runtime.dto.ReferenceDTO

/**
 * A bare-bones implementation of
 * [OSGi Component Constructor Injection](https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.component.html#service.component-constructor.injection)
 * This is sufficient to create instances of our PROTOTYPE-scoped services inside a sandbox.
 */
class ServiceDefinition(
    val serviceClassNames: Set<String>,
    private val description: ComponentDescriptionDTO
) {
    private companion object {
        private const val PROTOTYPE_SERVICE = "($SERVICE_SCOPE=$SCOPE_PROTOTYPE)"
        private const val NOT_IN_SANDBOX = "(&(!$PROTOTYPE_SERVICE)(!$CORDA_SANDBOX_FILTER))"
        private val activationFields = setOf(BundleContext::class.java, Map::class.java)
    }

    constructor(serviceClasses: List<Class<*>>, description: ComponentDescriptionDTO)
        : this(serviceClasses.mapTo(LinkedHashSet(), Class<*>::getName), description)

    constructor(description: ComponentDescriptionDTO) : this(emptySet(), description)

    private val referencedServiceTypes = description.references.mapTo(LinkedHashSet(), ReferenceDTO::interfaceName)
    private val _references = mutableMapOf<String, Set<ServiceReference<*>>>()
    private var broken: Boolean = false

    // If we try and FAIL to create this service then mark is as "broken".
    // This saves us from trying to create it again (and again).
    val isBroken: Boolean
        get() = broken

    // We only support services that are created using constructor injection.
    val isByConstructor: Boolean
        get() = description.references.all { it.bind == null && it.field == null }

    // These are the service references that should be satisfied from within the sandbox.
    val sandboxReferences: Map<String, Set<ServiceReference<*>>>
        get() = unmodifiableMap(_references)

    fun initialise(serviceIndex: Map<String, Set<ServiceReference<*>>>): ServiceDefinition {
        referencedServiceTypes.forEach { serviceType ->
            serviceIndex[serviceType]?.also { refs ->
                _references[serviceType] = refs
            }
        }
        return this
    }

    fun broken(): ServiceDefinition {
        broken = true
        return this
    }

    @Suppress("ComplexMethod", "SpreadOperator")
    fun createInstance(bundle: Bundle, sandboxServices: SatisfiedServiceReferences): Pair<Any, Collection<AutoCloseable>> {
        // Analyse what we know about these references so that we can choose the correct public constructor.
        val parameterDTOs = Array<Pair<ReferenceDTO, Class<*>>?>(description.init) { null }
        description.references.forEach { ref ->
            ref.parameter?.also { idx ->
                parameterDTOs[idx] = ref.cardinality.let { cardinality ->
                    Pair(ref, when {
                        // No explicit cardinality, so it will be determined by constructor's parameter type.
                        cardinality === null -> Void::class.java

                        // Multiple cardinality, so we need something assignable to either Collection or List.
                        cardinality.endsWith("..n") -> List::class.java

                        // Unary cardinality, i.e. assignable to the service type.
                        else -> bundle.loadClass(ref.interfaceName)
                    })
                }
            }
        }

        val closeables = LinkedList<AutoCloseable>()
        try {
            // Find a public constructor that can accept these parameters, and invoke it.
            val serviceClass = bundle.loadClass(description.implementationClass)
            return serviceClass.constructors.firstOrNull { ctor ->
                ctor.parameterCount == description.init && matchParameterTypes(ctor, parameterDTOs)
            }?.let { ctor ->
                // Allow instantiation of classes which are non-public.
                ctor.isAccessible = true

                val properties = LinkedHashMap(description.properties ?: emptyMap()).let { props ->
                    props[COMPONENT_NAME] = description.name
                    unmodifiableMap(props)
                }
                val parameterFactory = ParameterFactory(
                    sandboxServices,
                    bundle.bundleContext,
                    properties,
                    closeables
                )
                val parameterValues = Array(ctor.parameterCount) { idx ->
                    val parameter = ctor.parameters[idx]
                    val (reference, parameterType) =
                        (parameterDTOs[idx] ?: return@Array parameterFactory.getActivationField(parameter))
                    when {
                        // No explicit cardinality, so refer to constructor's parameter type.
                        parameterType === Void::class.java ->
                            if (parameter.type.isAssignableFrom(List::class.java)) {
                                parameterFactory.getMultipleServices(reference)
                            } else {
                                parameterFactory.getSingleService(reference)
                            }

                        // Reference has multiple cardinality.
                        parameterType === List::class.java ->
                            parameterFactory.getMultipleServices(reference)

                        // Reference has unary cardinality.
                        else ->
                            parameterFactory.getSingleService(reference)
                    }
                }

                Pair(ctor.newInstance(*parameterValues), closeables)
            } ?: throw IllegalStateException("No suitable constructor found for ${serviceClass.name}")
        } catch (e: Exception) {
            closeables.forEach(::closeSafely)
            broken()
            throw e
        }
    }

    private fun matchParameterTypes(ctor: Constructor<*>, parameterDTOs: Array<Pair<ReferenceDTO, Class<*>>?>): Boolean {
        ctor.parameters.forEachIndexed { idx, param ->
            val parameterDTO = parameterDTOs[idx]
            if (parameterDTO == null) {
                if (param.type !in activationFields) {
                    throw IllegalStateException("$ctor has unsupported parameter type ${param.type.name}")
                }
            } else {
                val parameterType = parameterDTO.second
                if (parameterType !== Void::class.java && !param.type.isAssignableFrom(parameterType)) {
                    return false
                }
            }
        }
        return true
    }

    override fun toString(): String {
        return "ServiceDefinition[component=${description.name}, services=$serviceClassNames]"
    }

    private class ParameterFactory(
        private val sandboxServices: SatisfiedServiceReferences,
        private val bundleContext: BundleContext,
        private val properties: Map<String, Any>,
        private val closeables: Deque<AutoCloseable>
    ) {
        private fun getServiceFilter(target: String?): String {
            return target?.let { filter -> "(&$NOT_IN_SANDBOX$filter)" } ?: NOT_IN_SANDBOX
        }

        private fun findSandboxServices(reference: ReferenceDTO): Collection<Any>? {
            return sandboxServices[reference.interfaceName]?.let { services ->
                reference.target?.let { target ->
                    val filter = bundleContext.createFilter(target)
                    services.filterKeys(filter::match)
                } ?: services
            }?.values
        }

        private fun findServiceReferences(reference: ReferenceDTO): Set<ServiceReference<*>> {
            return bundleContext.getServiceReferences(reference.interfaceName, getServiceFilter(reference.target))
                ?.toSortedSet(reverseOrder())
                ?: emptySet()
        }

        private fun getService(svcRef: ServiceReference<*>): Any? {
            return bundleContext.getService(svcRef)?.also {
                closeables.addFirst(AutoCloseable { bundleContext.ungetService(svcRef) })
            }
        }

        fun getSingleService(reference: ReferenceDTO): Any? {
            return findSandboxServices(reference)?.firstOrNull() ?: run {
                findServiceReferences(reference).firstOrNull()
                    ?.let(::getService)
                    .also { svc ->
                        if (svc == null && !reference.cardinality.startsWith("0..")) {
                            throw IllegalArgumentException("Missing mandatory reference for ${reference.interfaceName}")
                        }
                    }
            }
        }

        fun getMultipleServices(reference: ReferenceDTO): List<*> {
            // Accept services from both inside and outside the sandbox,
            // subject to our sandbox visibility rules.
            return ((findSandboxServices(reference) ?: emptyList()) +
                findServiceReferences(reference).mapNotNull(::getService)).also { svcs ->
                    if (svcs.isEmpty() && !reference.cardinality.startsWith("0..")) {
                        throw IllegalArgumentException("Missing mandatory reference for ${reference.interfaceName}")
                    }
            }
        }

        fun getActivationField(parameter: Parameter): Any {
            return if (parameter.type.isAssignableFrom(Map::class.java)) {
                properties
            } else {
                bundleContext
            }
        }
    }
}
