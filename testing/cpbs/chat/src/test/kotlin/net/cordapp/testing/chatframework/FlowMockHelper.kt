package net.cordapp.testing.chatframework

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

/**
 * A Helper class which injects Mockito mock services into Flows in order to unit test Flows.
 * To construct a FlowMockHelper in Kotlin use the DSL builder as follows:
 * ```kotlin
 *     val flowMockHelper = FlowMockHelper {
 *         createMockService<FlowMessaging>()
 *         createMockService<FlowEngine>()
 *         createMockService<JsonMarshallingService>()
 *     }
 * ```
 * Note that each mockService<>() declaration returns that mock service such that it can be further customised inline
 * during the building. For example:
 * ```kotlin
 *     mockService<FlowEngine>().withVirtualNodeName(FROM_X500_NAME)
 * ```
 *
 * To construct a FlowMockHelper in Java do something like the following:
 * ```java
 *     FlowMockHelper outgoingFlowMockHelper = FlowMockHelper.fromInjectableServices(
 *             new InjectableMockServices()
 *                     .createMockService(FlowMessaging.class)
 *                     .createMockService(JsonMarshallingService.class)
 *                     .createMockService(FlowEngine.class));
 * ```
 *
 * In either language createFlow()/createFlow<>() can then be called to instantiate a Flow with the necessary mock
 * services injected.
 * Setup of mock services is service dependent and not the role of the FlowMockHelper itself.
 */
class FlowMockHelper(init: InjectableMockServices.() -> Unit) {
    init {
        InjectableMockServices().apply(init).also {
            this.serviceTypeMap = it.serviceTypeMap
        }
    }

    companion object {
        /**
         * Create a FlowMockHelper from a pre-populated injectableMockServices. Used to instantiate a FlowMockHelper
         * from Java.
         */
        @JvmStatic
        fun fromInjectableServices(injectableMockServices: InjectableMockServices) = FlowMockHelper {}.apply {
            this.serviceTypeMap = injectableMockServices.serviceTypeMap
        }
    }

    private var serviceTypeMap: Map<Class<*>, Any>
    var flow: Flow? = null

    /**
     * Returns a mock service by type. For convenience it is recommended to use serviceMock<>() instead of
     * this method.
     */
    fun getMockService(clazz: Class<*>) = serviceTypeMap[clazz]

    private fun injectServices(flow: Flow) {
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

    fun <T : Flow> createFlow(clazz: Class<T>): T = clazz.getDeclaredConstructor().newInstance().also {
        injectServices(it)
    }

    /**
     * Only call this method if a FlowMessaging service has been mocked against this FlowMockHelper.
     * Sets up the FlowMessage mock associated with this FlowMockHelper such that it returns a FlowSession which is
     * also available to the test to verify expected messages are sent.
     * @return The mock FlowSession which will be returned by the mock FlowMessage. This is useful to retain if you wish to
     * verify() any actions were performed on it after the Flow exits.
     */
    fun expectFlowMessagesTo(memberX500Name: MemberX500Name) = mock<FlowSession>().also {
        whenever(this.getMockService<FlowMessaging>().initiateFlow(memberX500Name)).thenReturn(it)
    }
}

/**
 * Instantiates a Flow of the provided type and injects all services of this FlowMockHelper into it.
 * Only one Flow can be bound to one FlowMockHelper.
 * ```kotlin
 *     val outgoingChatFlow = outgoingFlowMockHelper.createFlow<ChatOutgoingFlow>()
 * ```
 */
inline fun <reified T : Flow> FlowMockHelper.createFlow() = createFlow(T::class.java)

/**
 * Returns a service mock from a FlowMockHelper in order to set expectations on it. For example:
 * ```kotlin
 *     whenever(readerFlowMockHelper.serviceMock<JsonMarshallingService>().format(expectedMessages))
 *         .thenReturn(jsonReturn)
 * ```
 */
inline fun <reified T : Any> FlowMockHelper.getMockService(): T {
    return this.getMockService(T::class.java) as T
}

/**
 * Part of the FlowMockHelper DSL builder.
 */
class InjectableMockServices {
    val serviceTypeMap: MutableMap<Class<*>, Any> = mutableMapOf()

    /**
     * Java builder
     */
    fun createMockService(clazz: Class<*>): InjectableMockServices {
        serviceTypeMap.put(clazz, Mockito.mock(clazz))
        return this
    }
}

/**
 * Part of the FlowMockHelper DSL builder.
 */
inline fun <reified T : Any> InjectableMockServices.createMockService(): T = mock<T>().also {
    this.serviceTypeMap.put(T::class.java, it)
}

/**
 * Generates a mock which will return the passed object to any call to parse json along the lines of
 * requestBody.getRequestBodyAs<T>(jsonMarshallingService) inside the Flow.
 * This method allows the injection of RPC parameters to a Flow without having to test/mock json masrshalling
 * whilst ensuring the Flow implementation under test is using the correct JsonMarshallingService itself.
 * Typical use would be to pass the output of this method directly to the call() invocation on a Flow:
 * ```kotlin
 *     flow.call(
 *         flowMockHelper.rpcRequestGenerator(
 *             OutgoingChatMessage(recipientX500Name = RECIPIENT_X500_NAME)
 *         )
 *     }
 * ```
 * @return A mock RPCRequestData set up to return the correct object when queried for it
 */
inline fun <reified T> FlowMockHelper.rpcRequestGenerator(parameterObject: T) = mock<RPCRequestData>()
    .also {
        whenever(
            it.getRequestBodyAs(this.getMockService<JsonMarshallingService>(), T::class.java)
        ).thenReturn(parameterObject)
    }

inline fun <reified T : Any> FlowMockHelper.returnOnFind(findKey: Any, result: T?) {
    whenever(
        this.getMockService<PersistenceService>()
            .find(T::class.java, findKey)
    ).thenReturn(result)
}

/**
 * When merge is called in the Flow created by this helper, set up a corresponding Find to return the results of that
 * merge. The Flow in which Find is called can be configured by passing its helper.
 * @param helperForFlowCallingFind The FlowMockHelper which is tied to the Flow which is expected to call find
 * @param findKey The key which is expected to be passed to the find call
 * @param keyExtractor A lambda which will extract a key from the type of data being persisted. This is used to validate
 * the parameter passed to merge was the expected key
 * @param mergeOperation The merge operation, a simulation of what the persistence service would do
 */
inline fun <reified T : Any> FlowMockHelper.expectMergeAndLinkToFind(
    helperForFlowCallingFind: FlowMockHelper,
    findKey: Any,
    crossinline keyExtractor: (T) -> Any,
    crossinline mergeOperation: (T) -> T
) {
    whenever(
        this.getMockService<PersistenceService>().merge(any<T>())
    ).then {
        // Validate merge is occurring on same key
        val mergeParam = it.arguments[0] as T
        Assertions.assertThat(keyExtractor(mergeParam)).isEqualTo(findKey)
        // Merge
        val toReturn = mergeOperation(mergeParam)
        // Tie find to return
        whenever(
            helperForFlowCallingFind.getMockService<PersistenceService>()
                .find(T::class.java, findKey)
        ).thenReturn(toReturn)
    }
}

/**
 * When persist is called in the Flow created by this helper, set up a corresponding Find to return the results of that
 * persist. The Flow in which Find is called can be configured by passing its helper.
 * @param helperForFlowCallingFind The FlowMockHelper which is tied to the Flow which is expected to call find
 * @param findKey The key which is expected to be passed to the find call
 */
inline fun <reified T : Any> FlowMockHelper.expectPersistAndLinkToFind(
    helperForFlowCallingFind: FlowMockHelper,
    findKey: Any
) {
    whenever(
        this.getMockService<PersistenceService>().persist(any<T>())
    ).then {
        val toReturn = it.arguments[0] as T
        // Tie find to return
        whenever(
            helperForFlowCallingFind.getMockService<PersistenceService>()
                .find(T::class.java, findKey)
        ).thenReturn(toReturn)
    }
}