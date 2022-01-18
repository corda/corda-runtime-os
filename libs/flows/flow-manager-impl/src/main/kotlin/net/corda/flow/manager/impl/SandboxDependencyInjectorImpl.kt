package net.corda.flow.manager.impl

import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.lang.reflect.Field
import java.util.*

class SandboxDependencyInjectorImpl(
    singletons: List<SingletonSerializeAsToken>
) : SandboxDependencyInjector {

    private val serviceTypeMap: MutableMap<Class<*>, SingletonSerializeAsToken> =
        Collections.synchronizedMap(mutableMapOf<Class<*>, SingletonSerializeAsToken>())

    init {
        singletons.forEach { registerService(it) }
    }

    override fun injectServices(flow: Flow<*>) {

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
                    serviceTypeMap[field.type]
                )
            }
        }
    }

    override fun getRegisteredSingletons(): Set<SingletonSerializeAsToken> {
        return serviceTypeMap.values.toSet()
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

    private fun registerService(service: SingletonSerializeAsToken) {
        val implementedServiceTypes = getTypesImplementedByService(service.javaClass)
        implementedServiceTypes.forEach { registerServiceImplementation(service, it) }
    }

    private fun registerServiceImplementation(service: SingletonSerializeAsToken, implementedServiceType: Class<*>) {
        val existingService = serviceTypeMap.putIfAbsent(implementedServiceType, service)
        if (existingService != null) {
            throw IllegalArgumentException(
                "An implementation of type '${implementedServiceType.name}' has been already been registered by " +
                        "'${existingService.javaClass.name}' it can't be registered " +
                        "again by '${service.javaClass.name}'."
            )
        }
    }

    private fun getTypesImplementedByService(serviceClass: Class<*>): List<Class<*>> {
        /*
        OSGI will pass in an implementation class, so we need to determine what abstract types the
        services implement, for now we se a dumb reflection strategy, this might need to be improved at some point
        and made explicit with a different approach.
         */

        // If the service type is an interface then assume it can be registered as is.
        if (serviceClass.isInterface) {
            return listOf(serviceClass)
        }

        // associate the service will all the interfaces it implements, excluding common shared types.
        return serviceClass.interfaces.filterNot {
            it == SingletonSerializeAsToken::class.java || it == CordaFlowInjectable::class.java
        }.toList()
    }
}

