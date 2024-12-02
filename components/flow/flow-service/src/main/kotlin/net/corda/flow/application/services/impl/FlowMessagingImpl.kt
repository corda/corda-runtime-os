package net.corda.flow.application.services.impl

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.flow.application.serialization.DeserializedWrongAMQPObjectException
import net.corda.flow.application.serialization.FlowSerializationService
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.application.sessions.utils.SessionUtils.checkPayloadMaxSize
import net.corda.flow.application.sessions.utils.SessionUtils.verifySessionStatusNotErrorOrClose
import net.corda.flow.application.versioning.impl.sessions.VersionReceivingFlowSession
import net.corda.flow.application.versioning.impl.sessions.VersionSendingFlowSession
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.FlowSessionConfiguration
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.time.Duration
import java.util.UUID

@Suppress("TooManyFunctions")
@Component(service = [FlowMessaging::class, UsedByFlow::class], scope = PROTOTYPE)
class FlowMessagingImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory,
    @Reference(service = FlowSerializationService::class)
    private val serializationService: FlowSerializationService
) : FlowMessaging, UsedByFlow, SingletonSerializeAsToken {

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    @Suspendable
    override fun initiateFlow(x500Name: MemberX500Name): FlowSession {
        return initiateFlow(x500Name, true)
    }

    @Suspendable
    override fun initiateFlow(x500Name: MemberX500Name, requireClose: Boolean): FlowSession {
        return doInitiateFlow(x500Name, requireClose)
    }

    @Suspendable
    override fun initiateFlow(x500Name: MemberX500Name, sessionConfiguration: FlowSessionConfiguration): FlowSession {
        return doInitiateFlow(x500Name, sessionConfiguration.isRequireClose, sessionConfiguration.timeout)
    }

    @Suspendable
    override fun initiateFlow(x500Name: MemberX500Name, flowContextPropertiesBuilder: FlowContextPropertiesBuilder): FlowSession {
        return doInitiateFlow(x500Name, true, sessionTimeout = null, flowContextPropertiesBuilder)
    }

    @Suspendable
    override fun initiateFlow(
        x500Name: MemberX500Name,
        requireClose: Boolean,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder
    ): FlowSession {
        return doInitiateFlow(x500Name, requireClose, sessionTimeout = null, flowContextPropertiesBuilder)
    }

    @Suspendable
    override fun initiateFlow(
        x500Name: MemberX500Name,
        sessionConfiguration: FlowSessionConfiguration,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder
    ): FlowSession {
        return doInitiateFlow(
            x500Name,
            sessionConfiguration.isRequireClose,
            sessionConfiguration.timeout,
            flowContextPropertiesBuilder
        )
    }

    @Suspendable
    override fun <R : Any> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<R> {
        @Suppress("unchecked_cast")
        val flowSessionInternals = sessions as Set<FlowSessionInternal>

        flowSessionInternals.forEach { session ->
            verifySessionStatusNotErrorOrClose(session.getSessionId(), flowFiberService)
        }

        val versionReceivingFlowSessionPayloads =
            getVersionReceivingFlowSessionPayloads(receiveType, flowSessionInternals)

        if (flowSessionInternals == versionReceivingFlowSessionPayloads.keys) {
            return versionReceivingFlowSessionPayloads.values.toList()
        }

        val sessionsToReceiveFrom = flowSessionInternals - versionReceivingFlowSessionPayloads.keys

        val versionSendingFlowSessionPayloads = getVersionSendingFlowSessionPayloads(sessionsToReceiveFrom)

        if (versionSendingFlowSessionPayloads.isNotEmpty()) {
            fiber.suspend(FlowIORequest.Send(versionSendingFlowSessionPayloads))
        }

        val request = FlowIORequest.Receive(sessions = sessionsToReceiveFrom.map { it.getSessionInfo() }.toSet())
        val received = fiber.suspend(request)
        setSessionsAsConfirmed(flowSessionInternals)
        return deserializeReceivedPayload(received, receiveType) + versionReceivingFlowSessionPayloads.values
    }

    @Suspendable
    override fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, Any> {
        val flowSessionInternals = sessions.mapKeys {
            it.key as FlowSessionInternal
        }

        flowSessionInternals.forEach { session ->
            verifySessionStatusNotErrorOrClose(session.key.getSessionId(), flowFiberService)
        }

        val versionReceivingFlowSessionPayloads = getVersionReceivingFlowSessionPayloads(flowSessionInternals)

        if (versionReceivingFlowSessionPayloads.keys == sessions.keys) {
            @Suppress("unchecked_cast")
            return versionReceivingFlowSessionPayloads as Map<FlowSession, Any>
        }

        val sessionsToReceiveFrom = flowSessionInternals - versionReceivingFlowSessionPayloads.keys

        val versionSendingFlowSessionPayloads = getVersionSendingFlowSessionPayloads(sessionsToReceiveFrom)

        if (versionSendingFlowSessionPayloads.isNotEmpty()) {
            fiber.suspend(FlowIORequest.Send(versionSendingFlowSessionPayloads))
        }

        val request = FlowIORequest.Receive(
            sessions = sessionsToReceiveFrom.map {
                val flowSessionInternal = it.key
                flowSessionInternal.getSessionInfo()
            }.toSet()
        )

        val received = fiber.suspend(request)
        setSessionsAsConfirmed(flowSessionInternals.keys)
        return deserializeReceivedPayload(received, sessionsToReceiveFrom) + versionReceivingFlowSessionPayloads
    }

    @Suspendable
    override fun sendAll(payload: Any, sessions: Set<FlowSession>) {
        if (sessions.isEmpty()) {
            return
        }
        @Suppress("unchecked_cast")
        val flowSessionInternals = sessions as Set<FlowSessionInternal>
        val serializedPayload = serialize(payload)
        checkPayloadMaxSize(serializedPayload, flowFiberService)
        val sessionToPayload = flowSessionInternals.associate { session ->
            verifySessionStatusNotErrorOrClose(session.getSessionId(), flowFiberService)
            session.getSessionInfo() to when (session) {
                is VersionSendingFlowSession -> session.getPayloadToSend(serializedPayload)
                else -> serializedPayload
            }
        }
        fiber.suspend(FlowIORequest.Send(sessionToPayload))
        setSessionsAsConfirmed(flowSessionInternals)
    }

    @Suspendable
    override fun sendAllMap(payloadsPerSession: Map<FlowSession, Any>) {
        if (payloadsPerSession.isEmpty()) {
            return
        }
        val sessionPayload = payloadsPerSession.map { (session, payload) ->
            val flowSessionInternal = session as FlowSessionInternal
            verifySessionStatusNotErrorOrClose(session.getSessionId(), flowFiberService)
            val serializedPayload = serialize(payload)
            checkPayloadMaxSize(serializedPayload, flowFiberService)
            flowSessionInternal.getSessionInfo() to when (session) {
                is VersionSendingFlowSession -> session.getPayloadToSend(serializedPayload)
                else -> serializedPayload
            }
        }.toMap()
        fiber.suspend(FlowIORequest.Send(sessionPayload))
        @Suppress("unchecked_cast")
        setSessionsAsConfirmed(payloadsPerSession.keys as Set<FlowSessionInternal>)
    }

    private fun setSessionsAsConfirmed(flowSessionInternals: Set<FlowSessionInternal>) {
        flowSessionInternals.onEach { it.setSessionConfirmed() }
    }

    @Suspendable
    private fun doInitiateFlow(
        x500Name: MemberX500Name,
        requireClose: Boolean,
        sessionTimeout: Duration? = null,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder? = null
    ): FlowSession {
        val sessionId = UUID.randomUUID().toString()
        checkFlowCanBeInitiated()
        addSessionIdToFlowStackItem(sessionId)
        return flowSessionFactory.createInitiatingFlowSession(
            sessionId,
            requireClose,
            sessionTimeout,
            x500Name,
            flowContextPropertiesBuilder
        )
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
            val bytes = received[sessionId]
                ?: throw CordaRuntimeException("Unexpected error. $sessionId not found in received data.")
            deserializeReceivedPayload(sessionId, bytes, it.value)
        } as Map<FlowSession, Any>
    }

    private fun <R : Any> deserializeReceivedPayload(
        sessionId: String,
        bytes: ByteArray,
        receiveType: Class<R>
    ) = try {
        serializationService.deserializeAndCheckType(bytes, receiveType.kotlin.javaObjectType)
    } catch (e: DeserializedWrongAMQPObjectException) {
        val expectedType = e.expectedType
        val deserializedType = e.deserializedType
        val deserializedObject = e.deserializedObject

        throw CordaRuntimeException(
            "Expecting to receive a $expectedType but received a $deserializedType instead from session $sessionId, " +
                    "payload: ($deserializedObject)"
        )
    }

    private fun <R : Any> getVersionReceivingFlowSessionPayloads(
        receiveType: Class<out R>,
        flowSessionInternals: Set<FlowSessionInternal>
    ): Map<VersionReceivingFlowSession, R> {
        return flowSessionInternals
            .filterIsInstance<VersionReceivingFlowSession>()
            .mapNotNull { session ->
                session.getInitialPayloadIfNotAlreadyReceived(receiveType)
                    ?.let { payload -> session to payload }
            }.toMap()
    }

    private fun getVersionReceivingFlowSessionPayloads(
        flowSessionInternals: Map<FlowSessionInternal, Class<out Any>>
    ): Map<VersionReceivingFlowSession, Any> {
        return flowSessionInternals
            .filterKeys { session -> session is VersionReceivingFlowSession }
            .mapNotNull { (session, receiveType) ->
                (session as VersionReceivingFlowSession).getInitialPayloadIfNotAlreadyReceived(receiveType)
                    ?.let { payload -> session to payload }
            }.toMap()
    }

    private fun getVersionSendingFlowSessionPayloads(flowSessionInternals: Set<FlowSessionInternal>): Map<SessionInfo, ByteArray> {
        return flowSessionInternals
            .filterIsInstance<VersionSendingFlowSession>()
            .mapNotNull { session ->
                session.getVersioningPayloadToSend()
                    ?.let { payload -> session.getSessionInfo() to payload }
            }.toMap()
    }

    private fun getVersionSendingFlowSessionPayloads(
        flowSessionInternals: Map<FlowSessionInternal, Class<out Any>>
    ): Map<SessionInfo, ByteArray> {
        return flowSessionInternals
            .filterKeys { session -> session is VersionSendingFlowSession }
            .mapNotNull { (session, _) ->
                (session as VersionSendingFlowSession).getVersioningPayloadToSend()
                    ?.let { payload -> session.getSessionInfo() to payload }
            }.toMap()
    }
}
