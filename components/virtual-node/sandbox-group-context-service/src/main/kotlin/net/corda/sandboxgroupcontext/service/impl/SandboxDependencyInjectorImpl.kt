package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.FrameworkUtil
import java.lang.reflect.Field
import java.util.Collections.unmodifiableMap

class SandboxDependencyInjectorImpl<T : Any>(
    singletons: Map<SingletonSerializeAsToken, List<String>>,
    private val closeable: AutoCloseable
) : SandboxDependencyInjector<T> {
    private val serviceTypeMap: Map<Class<*>, SingletonSerializeAsToken>

    init {
        val serviceTypes = mutableMapOf<Class<*>, SingletonSerializeAsToken>()
        singletons.forEach { singleton ->
            registerService(singleton.key, singleton.value, serviceTypes)
        }
        serviceTypeMap = unmodifiableMap(serviceTypes)
    }

    override fun close() {
        closeable.close()
    }

    override fun injectServices(obj: T) {
        val requiredFields = obj::class.java.getFieldsForInjection()
        val mismatchedFields = requiredFields.filterNot { serviceTypeMap.containsKey(it.type) }
        if (mismatchedFields.any()) {
            val fields = mismatchedFields.joinToString(separator = ", ", transform = Field::getName)
            throw IllegalArgumentException(
                "No registered types could be found for the following field(s) '$fields'"
            )
        }

        requiredFields.forEach { field ->
            field.isAccessible = true
            if (field.get(obj) == null) {
                field.set(
                    obj,
                    serviceTypeMap[field.type]
                )
            }
        }
    }

    override fun getRegisteredServices(): Collection<SingletonSerializeAsToken> {
        return serviceTypeMap.values
    }

    /**
     * Get the declared fields of the current [Class], and of the superclasses of this [Class].
     * We get declared fields to include fields of all accessibility types.
     * Finally, we need to filter so that only fields annotated with [CordaInject] are returned.
     */
    private fun Class<*>.getFieldsForInjection(): Collection<Field> {
        return getSuperClassesFor(this).flatMap { it.declaredFields.toSet() }
            .filter { field ->
                field.isAnnotationPresent(CordaInject::class.java)
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

    private fun registerService(
        serviceObj: SingletonSerializeAsToken,
        serviceTypeNames: List<String>,
        serviceTypes: MutableMap<Class<*>, SingletonSerializeAsToken>
    ) {
        val serviceClass = serviceObj::class.java
        val serviceClassLoader = serviceClass.classLoader
        serviceTypeNames.mapNotNull { serviceTypeName ->
                try {
                    FrameworkUtil.getBundle(serviceClass)?.loadClass(serviceTypeName)
                        ?: Class.forName(serviceTypeName, false, serviceClassLoader)
                } catch (_: ClassNotFoundException) {
                    null
                }
            }.filter { serviceType ->
                // Check that serviceObj is assignable to serviceType.
                // Technically speaking, the OSGi framework should
                // already guarantee this for an OSGi service.
                serviceType.isInstance(serviceObj)
            }.ifEmpty {
                // Fall back to using the object's own class.
                listOf(serviceClass)
            }.forEach { implementedServiceType ->
                registerServiceImplementation(serviceObj, implementedServiceType, serviceTypes)
            }
    }

    private fun registerServiceImplementation(
        service: SingletonSerializeAsToken,
        implementedServiceType: Class<*>,
        serviceTypes: MutableMap<Class<*>, SingletonSerializeAsToken>
    ) {
        val existingService = serviceTypes.putIfAbsent(implementedServiceType, service)
        if (existingService != null) {
            throw IllegalArgumentException(
                "An implementation of type '${implementedServiceType.name}' has been already been registered by " +
                        "'${existingService.javaClass.name}' it can't be registered " +
                        "again by '${service.javaClass.name}'."
            )
        }
    }
}