package net.cordapp.testing.chatframework

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import org.mockito.kotlin.mock
import java.lang.reflect.Field

/**
 * A Helper class which injects Mockito mock services into Flows in order to unit test Flows.
 * To construct a FlowMockHelper in Kotlin use the DSL builder as follows:
 * <pre>
 *     val flowMockHelper = FlowMockHelper {
 *         mockService<FlowMessaging>()
 *         mockService<FlowEngine>()
 *         mockService<JsonMarshallingService>()
 *     }
 * </pre>
 * Note that each mockService<>() declaration returns that mock service such that it can be further customised inline
 * during the building. For example:
 * <pre>
 *     mockService<FlowEngine>().withVirtualNodeName(FROM_X500_NAME)
 * </pre>
 * Setup of mock services is service dependent and not the role of the FlowMockHelper itself.
}
 */
class FlowMockHelper(init: InjectableMockServices.() -> Unit) {
    init {
        InjectableMockServices().apply(init).also {
            this.serviceTypeMap = it.serviceTypeMap
        }
    }

    private var serviceTypeMap: Map<Class<*>, Any>
    var flow:Flow? = null

    /**
     * Returns a mock service by type. For convenience it is recommended to use serviceMock<>() instead of
     * this method.
     */
    fun getMockService(clazz :Class<*>) = serviceTypeMap[clazz]

    /**
     * Injects services into a Flow. For convenience it is recommended to use createFlow<>() instead of
     * this method which constructs the Flow under test as well as injecting the services.
     */
    fun injectServices(flow: Flow) {
        this.flow?.let {
            throw IllegalStateException("This FlowMockHelper is already bound to a flow")
        }
        this.flow = flow

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

/**
 * Instantiates a Flow of the provided type and injects all services of this FlowMockHelper into it.
 * Only one Flow can be bound to one FlowMockHelper.
 * <pre>
 *     val outgoingChatFlow = outgoingFlowMockHelper.createFlow<ChatOutgoingFlow>()
 * </pre>
 */
inline fun <reified T : Flow> FlowMockHelper.createFlow() = T::class.java.getDeclaredConstructor().newInstance().also {
    this.injectServices(it)
}

/**
 * Returns a service mock from a FlowMockHelper in order to set expectations on it. For example:
 * <pre>
 *     whenever(readerFlowMockHelper.serviceMock<JsonMarshallingService>().format(expectedMessages))
 *         .thenReturn(jsonReturn)
 * </pre>
 */
inline fun <reified T : Any>FlowMockHelper.serviceMock(): T {
    return this.getMockService(T::class.java) as T
}

/**
 * Part of the FlowMockHelper DSL builder.
 */
class InjectableMockServices {
    val serviceTypeMap: MutableMap<Class<*>, Any> = mutableMapOf()
}

/**
 * Part of the FlowMockHelper DSL builder.
 */
inline fun <reified T : Any>InjectableMockServices.mockService(): T = mock<T>().also {
    serviceTypeMap.put(T::class.java, it)
}



