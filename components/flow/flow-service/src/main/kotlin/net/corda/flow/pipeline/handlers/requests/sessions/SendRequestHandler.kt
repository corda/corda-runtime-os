package net.corda.flow.pipeline.handlers.requests.sessions

import java.time.Instant
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.utils.keyValuePairListOf
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class SendRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService
) : FlowRequestHandler<FlowIORequest.Send> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.Send::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Send): WaitingFor {
        val sessionsNotInitiated = getSessionsNotInitiated(context, request)
        return if(sessionsNotInitiated.isNotEmpty()) {
            return WaitingFor(SessionConfirmation(sessionsNotInitiated.keys.toList(), SessionConfirmationType.INITIATE))
        }
        else { WaitingFor(net.corda.data.flow.state.waiting.Wakeup()) }
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Send): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        try {
            //generate init messages for sessions which do not exist yet
            val sessionsNotInitiated = getSessionsNotInitiated(context, request)
            if (sessionsNotInitiated.isNotEmpty()) {
                initiatedSessions(context, sessionsNotInitiated)
            }

            flowSessionManager.validateSessionStates(checkpoint, request.sessionToPayload.keys)
            flowSessionManager.sendDataMessages(checkpoint, request.sessionToPayload, Instant.now()).forEach { updatedSessionState ->
                checkpoint.putSessionState(updatedSessionState)
            }
        } catch (e: FlowSessionStateException) {
            log.info("Failed to send session data for session", e)
            throw FlowPlatformException(e.message, e)
        }

        val wakeup = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
        return context.copy(outputRecords = context.outputRecords + wakeup)
    }

    private fun initiatedSessions(context: FlowEventContext<Any>, sessionsNotInitiated: Map<String, MemberX500Name>) {
        val checkpoint = context.checkpoint

        // throw an error if the session already exists (shouldn't really get here for real, but for this class, it's not valid)
        val protocolStore = try {
            flowSandboxService.get(context.checkpoint.holdingIdentity).protocolStore
        } catch (e: Exception) {
            throw FlowTransientException(
                "Failed to create the flow sandbox for identity ${context.checkpoint.holdingIdentity}: ${e.message}",
                e
            )
        }

        val initiator = checkpoint.flowStack.nearestFirst { it.isInitiatingFlow }?.flowName
            ?: throw FlowFatalException("Flow stack is empty or did not contain an initiating flow in the stack")

        val (protocolName, protocolVersions) = protocolStore.protocolsForInitiator(initiator, context)

        checkpoint.putSessionStates(
            sessionsNotInitiated.entries.map {
                flowSessionManager.sendInitMessage(
                    checkpoint,
                    it.key,
                    it.value,
                    protocolName,
                    protocolVersions,
                    //todo - do i need to use the builder  logic from FlowSessionFactory instead of driect access from context
                    contextUserProperties = keyValuePairListOf(checkpoint.flowContext.flattenUserProperties()),
                    contextPlatformProperties = keyValuePairListOf(checkpoint.flowContext.flattenPlatformProperties()),
                    Instant.now()
                )
            }
        )
    }

    private fun getSessionsNotInitiated(context: FlowEventContext<Any>, sendRequest: FlowIORequest.Send): Map<String, MemberX500Name> {
        val checkpoint = context.checkpoint
        val sessionToPayload = sendRequest.sessionToCounterparty ?: return emptyMap()
        val missingSessionStates = sessionToPayload.keys.filter { checkpoint.getSessionState(it) == null }.toSet()
        return sessionToPayload.filter { missingSessionStates.contains(it.key) }
    }
}