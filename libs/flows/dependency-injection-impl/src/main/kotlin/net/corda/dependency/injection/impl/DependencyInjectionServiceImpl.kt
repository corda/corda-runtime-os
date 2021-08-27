package net.corda.dependency.injection.impl

import com.google.common.collect.MutableClassToInstanceMap
import net.corda.dependency.injection.CordaInjectableException
import net.corda.dependency.injection.DependencyInjectionService
import net.corda.dependency.injection.DependencyInjector
import net.corda.dependency.injection.DynamicPropertyInjectable
import net.corda.dependency.injection.FlowStateMachineInjectable
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.injection.CordaInjectPreStart
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.application.services.CordaService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import java.lang.reflect.Field
import java.util.Collections.synchronizedMap
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses

@Component(immediate = true, service = [DependencyInjectionService::class], scope = ServiceScope.SINGLETON)
class DependencyInjectionServiceImpl : DependencyInjectionService {
    private companion object {
        private val log = contextLogger()
    }

    // Interfaces we currently allow for injection. Used to verify valid interface registration.
    private val injectableInterfaces = setOf(
        CordaFlowInjectable::class.java,
        CordaServiceInjectable::class.java,
    )

    // Services which are created at injection time by invoking a lambda
    private val dynamicServices = synchronizedMap(mutableMapOf<String, DependencyInjector<out Any>>())

    // Services which are created once and can be injected into multiple different flows/services
    private val singletonServices = MutableClassToInstanceMap.create<Any>()

    /**
     * Register injectable services which need to be newly created for each injection by invoking a lambda.
     */
    override fun <U : T, T : Any> registerDynamicService(
        injectableInterface: Class<T>,
        implementationInjector: DependencyInjector<U>
    ): Boolean {
        if (!injectableInterface.isInjectable()) {
            log.debug("Attempted to register ${injectableInterface.simpleName} for injection but failed due to missing " +
                    "interface implementation. Please implement one of ${injectableInterfaces.joinToString(",") { it.simpleName }}"
            )
            return false
        }
        dynamicServices[injectableInterface.name] = implementationInjector
        log.debug("Registered ${injectableInterface.name} for dependency injection.")
        return true
    }

    /**
     * Register injectable singleton service.
     */
    override fun <U : T, T : Any> registerSingletonService(
        injectableInterface: Class<T>,
        implementation: U
    ): Boolean {
        if (!injectableInterface.isInjectable()) {
            log.debug("Attempted to register ${injectableInterface.simpleName} for injection but failed due to missing " +
                    "interface implementation. Please implement one of ${injectableInterfaces.joinToString(",") { it.simpleName }}"
            )
            return false
        }
        singletonServices.putInstance(injectableInterface, implementation)
        log.debug("Registered ${injectableInterface.name} for dependency injection.")
        return true
    }

    private fun getDynamicServiceInjector(injectableInterface: KClass<*>): DependencyInjector<out Any>? =
        dynamicServices[injectableInterface.qualifiedName!!]

    private fun getDynamicService(
        injectableInterface: KClass<*>,
        flowStateMachineInjectable: FlowStateMachineInjectable?,
        currentFlow: Flow<*>?,
        currentService: SerializeAsToken?
    ): Any? =
        getDynamicServiceInjector(injectableInterface)?.inject(flowStateMachineInjectable, currentFlow, currentService)

    private fun getSingletonService(injectableInterface: KClass<*>): Any? =
        singletonServices.getInstance(injectableInterface.java)

    private inline fun <reified T : Any> getInjectableImplementation(
        field: Field,
        flowStateMachineInjectable: FlowStateMachineInjectable?,
        currentFlow: Flow<*>?,
        currentService: SerializeAsToken?
    ): Any {
        val injectableInterface: KClass<*> = uncheckedCast(field.type.kotlin)
        val impl =
            (getDynamicService(injectableInterface, flowStateMachineInjectable, currentFlow, currentService)
                ?: getSingletonService(injectableInterface))
                ?.filterByType<T>()

        if (impl == null) {
            log.error(
                "Attempted to inject dependency but the interface ${field.type.kotlin.simpleName} does not have a " +
                        "${T::class.java.simpleName} implementation registered."
            )
            throw CordaInjectableException(
                "Dependency injection has no implementation of ${field.type.kotlin.simpleName} which also " +
                        "implements ${T::class.java.simpleName} ."
            )
        }
        return impl
    }

    /**
     * Inject instances of all [CordaInject] annotated properties for the FlowLogic instance.
     * The list of allowed interfaces to be injected is controlled by [DependencyInjectionService].
     */
    override fun injectDependencies(flow: Flow<*>, flowStateMachineInjectable: FlowStateMachineInjectable) {
        flow::class.getFieldsForInjection()
            .forEach { field ->
                field.isAccessible = true
                when (val implementation = getStateMachineInjectableOrNull(field.get(flow))) {
                    null -> field.set(
                        flow,
                        getInjectableImplementation<CordaFlowInjectable>(field, flowStateMachineInjectable, flow, null)
                    )
                    // Update the stateMachine reference if it is not valid
                    // This may happen after restarting a flow from an unstarted state.
                    else -> implementation.injectableProperty = flowStateMachineInjectable
                }
            }
    }

    /**
     * Safe-cast an object as a [DynamicPropertyInjectable] of type [FlowStateMachineInjectable].
     * Return null if the object is not a [DynamicPropertyInjectable], or if it is a [DynamicPropertyInjectable] but
     * not of type [FlowStateMachineInjectable].
     */
    @Suppress("UNCHECKED_CAST")
    private fun getStateMachineInjectableOrNull(obj: Any?) =
        obj as? DynamicPropertyInjectable<FlowStateMachineInjectable>

    override fun injectDependencies(service: SerializeAsToken) {
        service::class.getFieldsForInjection()
            .forEach { field ->
                field.isAccessible = true
                if (field.get(service) == null) {
                    field.set(
                        service,
                        getInjectableImplementation<CordaServiceInjectable>(field, null, null, service)
                    )
                }
            }
    }

    override fun injectPreStartDependencies(cordaService: CordaService) {
        cordaService::class.getFieldsForPreStartInjection()
            .forEach { field ->
                field.isAccessible = true
                if (field.get(cordaService) == null) {
                    field.set(
                        cordaService,
                        getInjectableImplementation<CordaServiceInjectable>(field, null, null, cordaService)
                    )
                }
            }
    }

    private fun Class<*>.isInjectable(): Boolean = injectableInterfaces.any { it.isAssignableFrom(this) }
}

private inline fun <reified T : Any> Any.filterByType(): Any? {
    return when {
        CordaFlowInjectable::class.java.isAssignableFrom(T::class.java) -> flowInjectableOrNull()
        CordaServiceInjectable::class.java.isAssignableFrom(T::class.java) -> cordaServiceInjectableOrNull()
        else -> null
    }
}

private fun Any.flowInjectableOrNull(): CordaFlowInjectable? = this as? CordaFlowInjectable
private fun Any.cordaServiceInjectableOrNull(): CordaServiceInjectable? = this as? CordaServiceInjectable

/**
 * Get the declared fields of the current KClass, and of the superclasses of this KClass.
 * We get declared fields to include fields of all accessibility types.
 * Finally we need to filter so that only fields annotated with [CordaInject] are returned.
 */
private fun KClass<*>.getFieldsForInjection(): Collection<Field> {
    return this.getFieldsForInjection(CordaInject::class.java)
}

/**
 * Get the declared fields of the current KClass, and of the superclasses of this KClass.
 * We get declared fields to include fields of all accessibility types.
 * Finally we need to filter so that only fields annotated with [CordaInjectPreStart] are returned.
 */
private fun KClass<*>.getFieldsForPreStartInjection(): Collection<Field> {
    return this.getFieldsForInjection(CordaInjectPreStart::class.java)
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