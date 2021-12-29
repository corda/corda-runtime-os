package net.corda.dependency.injection.impl

import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.dependency.injection.InjectableFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.lang.reflect.Field
import java.util.Collections

class FlowDependencyInjectorImpl(
    private val sandboxGroup: SandboxGroup
) : FlowDependencyInjector {

    constructor(
        sandboxGroup: SandboxGroup,
        services: List<InjectableFactory<*>>,
        singletons: List<SingletonSerializeAsToken>
    ) : this(sandboxGroup) {
        services.forEach(::registerServiceFactory)
        singletonList.addAll(singletons)
    }

    private val serviceTypeMap: MutableMap<Class<*>, InjectableFactory<*>> =
        Collections.synchronizedMap(mutableMapOf<Class<*>, InjectableFactory<*>>())

    private val singletonList = mutableSetOf<SingletonSerializeAsToken>()

    override fun injectServices(flow: Flow<*>, flowFiber: FlowFiber<*>) {
        val requiredFields = flow::class.java.getFieldsForInjection()
        val mismatchedFields = requiredFields.filterNot { serviceTypeMap.containsKey(it.type) }
        if (mismatchedFields.any()) {
            val fields = mismatchedFields.joinToString(separator = ", ", transform = Field::getName)
            throw IllegalArgumentException(
                "No registered types could be found for the following field(s) '$fields'"
            )
        }

        requiredFields.forEach { field ->
            field.isAccessible = true
            if (field.get(flow) == null) {
                field.set(
                    flow,
                    serviceTypeMap[field.type]!!.create(flowFiber, sandboxGroup)
                )
            }
        }
    }

    override fun getRegisteredAsTokenSingletons(): Set<SingletonSerializeAsToken> {
        return singletonList
    }

    private fun registerServiceFactory(serviceFactory: InjectableFactory<*>) {
        if (serviceTypeMap.putIfAbsent(serviceFactory.target, serviceFactory) != null) {
            throw IllegalArgumentException(
                "An instance of type '${serviceFactory.target.name}' has been already been registered."
            )
        }
    }

    /**
     * Get the declared fields of the current [Class], and of the superclasses of this [Class].
     * We get declared fields to include fields of all accessibility types.
     * Finally, we need to filter so that only fields annotated with [CordaInject] are returned.
     */
    private fun Class<*>.getFieldsForInjection(): Collection<Field> {
        return this.getFieldsForInjection(CordaInject::class.java)
    }

    private fun Class<*>.getFieldsForInjection(annotationType: Class<out Annotation>): Collection<Field> {
        return getSuperClassesFor(this).flatMap { it.declaredFields.toSet() }
            .filter { field ->
                field.isAnnotationPresent(annotationType)
            }
    }

    private fun getSuperClassesFor(clazz: Class<*>): List<Class<*>> {
        val superClasses = mutableListOf<Class<*>>()
        var target: Class<*>? = clazz
        while (target != null) {
            superClasses.add(target)
            target = target.superclass
        }
        return superClasses
    }
}
