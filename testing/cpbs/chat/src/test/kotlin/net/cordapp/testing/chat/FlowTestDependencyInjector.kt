package net.cordapp.testing.chat

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

class InjectableMockServices {
    val serviceTypeMap: MutableMap<Class<*>, Any> = mutableMapOf()
}

inline fun <reified T : Any>InjectableMockServices.mockService() {
    serviceTypeMap.put(T::class.java, mock<T>())
}

inline fun <reified T : Any>FlowTestDependencyInjector.serviceMock(): T {
    return this.getMockService(T::class.java) as T
}

class FlowTestDependencyInjector(init: InjectableMockServices.() -> Unit) {
    init {
        InjectableMockServices().apply(init).also {
            this.serviceTypeMap = it.serviceTypeMap
        }
    }

    private var serviceTypeMap: Map<Class<*>, Any>

    fun getMockService(clazz :Class<*>) = serviceTypeMap[clazz]

    fun injectServices(flow: Flow) {
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
}
