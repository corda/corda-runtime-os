package net.corda.dependency.injection.impl

import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.dependency.injection.InjectableFactory
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.lang.reflect.Field
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

class FlowDependencyInjectorImpl(
    private val sandboxGroup: SandboxGroup
) : FlowDependencyInjector {

    constructor(
        sandboxGroup: SandboxGroup,
        services: List<InjectableFactory<*>>,
        singletons: List<SingletonSerializeAsToken>
    ) : this(sandboxGroup) {
        services.forEach {
            registerServiceFactory(it)
            singletonList.addAll(singletons)
        }
    }

    private val serviceTypeMap: MutableMap<Class<*>, InjectableFactory<*>> =
        Collections.synchronizedMap(mutableMapOf<Class<*>, InjectableFactory<*>>())

    private val singletonList = mutableSetOf<SingletonSerializeAsToken>()

    override fun injectServices(flow: Flow<*>, flowStateMachine: FlowStateMachine<*>) {
        val requiredFields = flow::class.getFieldsForInjection()
        val mismatchedFields = requiredFields.filter { !serviceTypeMap.containsKey(it.type) }
        if (mismatchedFields.any()) {
            val fields = mismatchedFields.joinToString(separator = ", ") { it.name }
            throw IllegalArgumentException(
                "No registered types could be found for the following field(s) '$fields'"
            )
        }

        requiredFields.forEach { field ->
            field.isAccessible = true
            if (field.get(flow) == null) {
                field.set(
                    flow,
                    serviceTypeMap[field.type]!!.create(flowStateMachine, sandboxGroup)
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
     * Get the declared fields of the current KClass, and of the superclasses of this KClass.
     * We get declared fields to include fields of all accessibility types.
     * Finally, we need to filter so that only fields annotated with [CordaInject] are returned.
     */
    private fun KClass<*>.getFieldsForInjection(): Collection<Field> {
        return this.getFieldsForInjection(CordaInject::class.java)
    }

    @Suppress("SpreadOperator")
    private fun KClass<*>.getFieldsForInjection(annotationType: Class<out Annotation>): Collection<Field> {
        return setOf(
            this,
            *this.allSuperclasses.toTypedArray()
        )
            .flatMap { it.java.declaredFields.toSet() }
            .filter {
                it.isAnnotationPresent(annotationType)
            }
    }
}

