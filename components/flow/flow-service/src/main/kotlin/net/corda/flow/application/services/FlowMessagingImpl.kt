package net.corda.flow.application.services

import java.util.UUID
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.flow.application.serialization.DeserializedWrongAMQPObjectException
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("TooManyFunctions")
@Component(service = [ FlowMessaging::class, UsedByFlow::class ], scope = PROTOTYPE)
class FlowMessagingImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory,
    @Reference(service = SerializationServiceInternal::class)
    private val serializationService: SerializationServiceInternal
) : FlowMessaging, UsedByFlow, SingletonSerializeAsToken {

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    @Suspendable
    override fun initiateFlow(x500Name: MemberX500Name): FlowSession {
        return doInitiateFlow(x500Name, null)
    }

    @Suspendable
    override fun initiateFlow(
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder
    ): FlowSession {
        return doInitiateFlow(x500Name, flowContextPropertiesBuilder)
    }

    @CordaSerializable
    data class FacadeInvocation(
        val memberName: MemberX500Name,
        val facadeName: String,
        val methodName: String,
        val payload: String
    )

    @CordaSerializable
    data class FacadeInvocationResult(
        val result: String
    )

    @Suspendable
    override fun callFacade(
        memberName: MemberX500Name,
        facadeName: String,
        methodName: String,
        payload: String
    ): String {
        //val session = createInteropFlowSession(memberName)
        //val request = FacadeInvocation(memberName, facadeName, methodName, payload)
        //val response = session.sendAndReceive(FacadeInvocationResult::class.java, request)
        //session.close()
        //return response.result
        return payload
    }

    @Suspendable
    override fun <R: Any> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<R> {
        requireBoxedType(receiveType)
        val flowSessionInternals: Set<FlowSessionInternal> = uncheckedCast(sessions)
        val request = FlowIORequest.Receive(sessions = flowSessionInternals.map {
            it.getSessionInfo()
        }.toSet())
        val received = fiber.suspend(request)
        setSessionsAsConfirmed(flowSessionInternals)
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, Any> {
        val flowSessionInternals = sessions.mapKeys {
            requireBoxedType(it.value)
            it.key as FlowSessionInternal
        }
        val request = FlowIORequest.Receive(sessions = flowSessionInternals.map {
            val flowSessionInternal = it.key
            flowSessionInternal.getSessionInfo()
        }.toSet())
        val received = fiber.suspend(request)
        setSessionsAsConfirmed(flowSessionInternals.keys)
        return deserializeReceivedPayload(received, flowSessionInternals)
    }

    @Suspendable
    override fun sendAll(payload: Any, sessions: Set<FlowSession>) {
        requireBoxedType(payload::class.java)
        val flowSessionInternals: Set<FlowSessionInternal> = uncheckedCast(sessions)
        val serializedPayload = serialize(payload)
        val sessionToPayload =
            flowSessionInternals.associate { it.getSessionInfo() to serializedPayload }
        fiber.suspend(FlowIORequest.Send(sessionToPayload))
        setSessionsAsConfirmed(flowSessionInternals)
    }

    @Suspendable
    override fun sendAllMap(payloadsPerSession: Map<FlowSession, Any>) {
        val sessionPayload = payloadsPerSession.map {
            requireBoxedType(it.value::class.java)
            val flowSessionInternal = (it.key as FlowSessionInternal)
            flowSessionInternal.getSessionInfo() to serialize(it.value)
        }.toMap()
        fiber.suspend(FlowIORequest.Send(sessionPayload))
        setSessionsAsConfirmed(uncheckedCast(payloadsPerSession.keys))
    }

    private fun setSessionsAsConfirmed(flowSessionInternals: Set<FlowSessionInternal>) {
        flowSessionInternals.onEach { it.setSessionConfirmed() }
    }

    @Suspendable
    private fun doInitiateFlow(
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder?
    ): FlowSession {
        val sessionId = UUID.randomUUID().toString()
        checkFlowCanBeInitiated()
        addSessionIdToFlowStackItem(sessionId)
        return flowSessionFactory.createInitiatingFlowSession(sessionId, x500Name, flowContextPropertiesBuilder)
    }

    @Suspendable
    private fun createInteropFlowSession(x500Name: MemberX500Name): FlowSession {
        val sessionId = "${UUID.randomUUID()}-INTEROP"
        addSessionIdToFlowStackItem(sessionId)
        return flowSessionFactory.createInitiatingFlowSession(sessionId, x500Name, null)
    }

    private fun checkFlowCanBeInitiated() {
        val flowStackItem = getCurrentFlowStackItem()
        if (!flowStackItem.isInitiatingFlow) {
            throw CordaRuntimeException(
                "Cannot initiate flow inside of ${flowStackItem.flowName} as it is not annotated with @InitiatingFlow"
            )
        }
    }

    private fun addSessionIdToFlowStackItem(sessionId: String) {
        getCurrentFlowStackItem().sessions.add(FlowStackItemSession(sessionId, false))
    }

    private fun getCurrentFlowStackItem(): FlowStackItem {
        return flowFiberService
            .getExecutingFiber()
            .getExecutionContext()
            .flowStackService
            .peek()
            ?: throw CordaRuntimeException(
                "Flow [${flowFiberService.getExecutingFiber().flowId}] does not have a flow stack item"
            )
    }

    private fun serialize(payload: Any): ByteArray {
        return serializationService.serialize(payload).bytes
    }

    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun requireBoxedType(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    private fun <R : Any> deserializeReceivedPayload(
        received: Map<String, ByteArray>,
        receiveType: Class<R>
    ): List<R> {
        return received.map {
            deserializeReceivedPayload(it.key, it.value, receiveType)
        }
    }

    private fun deserializeReceivedPayload(
        received: Map<String, ByteArray>,
        receiveType: Map<FlowSessionInternal, Class<out Any>>
    ): Map<FlowSession, Any> {
        // Please DON'T use uncheckedCast() because its behaviour is
        // not guaranteed, and it often breaks when upgrading Kotlin!
        // It's more likely that your generic types are wrong anyway.
        @Suppress("unchecked_cast")
        return receiveType.mapValues {
            val sessionId = it.key.getSessionId()
            val bytes = received[sessionId] ?: throw CordaRuntimeException("Unexpected error. $sessionId not found in received data.")
            deserializeReceivedPayload(sessionId, bytes, it.value)
        } as Map<FlowSession, Any>
    }

    private fun <R : Any> deserializeReceivedPayload(
        sessionId: String,
        bytes: ByteArray,
        receiveType: Class<R>
    ) = try {
        serializationService.deserializeAndCheckType(bytes, receiveType)
    } catch (e: DeserializedWrongAMQPObjectException) {
        throw CordaRuntimeException(
            "Expecting to receive a ${e.expectedType} but received a ${e.deserializedType} instead from session $sessionId, " +
                    "payload: (${e.deserializedObject})"
        )
    }
}
