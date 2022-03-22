package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.addOrReplaceSession
import net.corda.flow.pipeline.handlers.getSerializationService
import net.corda.flow.pipeline.handlers.getSession
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.requireCheckpoint
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.session.manager.SessionManager
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SendRequestHandler @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    // could make a singleton that does the serialization and gets the underlying serialization service from the right sandbox as needed?
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowRequestHandler<FlowIORequest.Send> {

    override val type = FlowIORequest.Send::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Send): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Send): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        val serializationService = flowSandboxService.getSerializationService(checkpoint.flowKey.identity.toCorda())
        val serializedMessages = request.sessionToMessage.mapValues { (_, message) -> serializationService.serialize(message) }

        val now = Instant.now()

        for ((sessionId, serializedMessage) in serializedMessages) {

            val updatedSessionState = sessionManager.processMessageToSend(
                key = checkpoint.flowKey.flowId,
                sessionState = checkpoint.getSession(sessionId),
                event = SessionEvent.newBuilder()
                    .setSessionId(sessionId)
                    .setMessageDirection(MessageDirection.OUTBOUND)
                    .setTimestamp(now)
                    .setSequenceNum(null)
                    .setReceivedSequenceNum(0)
                    .setOutOfOrderSequenceNums(listOf(0))
                    .setPayload(SessionData(ByteBuffer.wrap(serializedMessage.bytes)))
                    .build(),
                instant = now
            )

            checkpoint.addOrReplaceSession(updatedSessionState)
        }

        val wakeup = Record(
            topic = Schemas.Flow.FLOW_EVENT_TOPIC,
            key = checkpoint.flowKey,
            value = FlowEvent(checkpoint.flowKey, Wakeup())
        )

        return context.copy(outputRecords = context.outputRecords + wakeup)
    }
}