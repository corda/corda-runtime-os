package net.corda.flow.application.services

import java.util.UUID
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.application.serialization.DeserializedWrongAMQPObjectException
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("TooManyFunctions")
@Component(service = [FlowMessaging::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class FlowMessagingImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory,
    @Reference(service = SerializationServiceInternal::class)
    private val serializationService: SerializationServiceInternal,
) : FlowMessaging, SingletonSerializeAsToken {

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

    @Suspendable
    override fun <R> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<R> {
        requireBoxedType(receiveType)
        val request = FlowIORequest.Receive(sessions = sessions.map {
            val flowSession = (it as FlowSessionInternal)
            FlowIORequest.SessionInfo(flowSession.getSessionId(), flowSession.counterparty)
        }.toSet())
        val received = fiber.suspend(request)
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, Any> {
        val flowSessionImpls = sessions.mapKeys {
            requireBoxedType(it.value)
            (it.key as FlowSessionInternal)
        }
        val request = FlowIORequest.Receive(sessions = sessions.map {
            val flowSession = (it as FlowSessionInternal)
            FlowIORequest.SessionInfo(flowSession.getSessionId(), flowSession.counterparty)
        }.toSet())
        val received = fiber.suspend(request)
        return deserializeReceivedPayload(received, flowSessionImpls)
    }

    @Suspendable
    override fun sendAll(payload: Any, sessions: Set<FlowSession>) {
        requireBoxedType(payload::class.java)
        val flowSessions = sessions.map { it as FlowSessionInternal }
        val serializedPayload = serialize(payload)
        val sessionToPayload =
            flowSessions.associate { FlowIORequest.SessionInfo(it.getSessionId(), it.counterparty) to serializedPayload }
        return fiber.suspend(FlowIORequest.Send(sessionToPayload))
    }

    @Suspendable
    override fun sendAllMap(payloadsPerSession: Map<FlowSession, Any>) {
        val sessionPayload = payloadsPerSession.map {
            requireBoxedType(it.value::class.java)
            val flowSessionInternal = (it.key as FlowSessionInternal)
            FlowIORequest.SessionInfo(flowSessionInternal.getSessionId(), flowSessionInternal.counterparty) to serialize(it.key)
        }.toMap()
        return fiber.suspend(FlowIORequest.Send(sessionPayload))
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

    private fun checkFlowCanBeInitiated() {
        val flowStackItem = getCurrentFlowStackItem()
        if (!flowStackItem.isInitiatingFlow) {
            throw CordaRuntimeException(
                "Cannot initiate flow inside of ${flowStackItem.flowName} as it is not annotated with @InitiatingFlow"
            )
        }
    }

    private fun addSessionIdToFlowStackItem(sessionId: String) {
        getCurrentFlowStackItem().sessionIds.add(sessionId)
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
        return uncheckedCast(receiveType.mapValues {
            val sessionId = it.key.getSessionId()
            val bytes = received[sessionId] ?: throw CordaRuntimeException("Unexpected error. $sessionId not found in received data.")
            deserializeReceivedPayload(sessionId, bytes, it.value)
        })
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
