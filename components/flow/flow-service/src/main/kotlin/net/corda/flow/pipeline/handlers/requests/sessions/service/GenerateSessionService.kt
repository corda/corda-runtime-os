package net.corda.flow.pipeline.handlers.requests.sessions.service

import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.keyValuePairListOf
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSIONS_SUPPORTED
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_REQUIRE_CLOSE
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_TIMEOUT_MS
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant

@Component(service = [GenerateSessionService::class])
class GenerateSessionService @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * For the given sessions in [sessionToInfo], return all the ones which do not have session states in the checkpoint yet.
     * @param context flow pipeline context
     * @param sessionToInfo sessions to check
     * @return sessions which have not been created in the checkpoint yet
     */
    fun getSessionsNotGenerated(
        context: FlowEventContext<Any>,
        sessionToInfo: Set<SessionInfo>
    ): Set<SessionInfo> {
        val checkpoint = context.checkpoint
        return sessionToInfo.filter { checkpoint.getSessionState(it.sessionId) == null }.toSet()
    }

    /**
     * For the given sessions in [sessionToInfo], generate session states and save them to the checkpoint.
     * @param context flow pipeline context
     * @param sessionToInfo sessions to create
     * @param sendCounterpartyRequest True to prepare a counterparty request to send when creating the session state.
     */
    fun generateSessions(
        context: FlowEventContext<Any>,
        sessionToInfo: Set<SessionInfo>,
        sendCounterpartyRequest: Boolean = false
    ) {
        val sessionsNotGenerated = getSessionsNotGenerated(context, sessionToInfo)
        if (sessionsNotGenerated.isNotEmpty()) {
            generateSessionStates(context, sessionsNotGenerated, sendCounterpartyRequest)
        }
    }

    @Suppress("ThrowsCount")
    private fun generateSessionStates(
        context: FlowEventContext<Any>,
        sessionsNotGenerated: Set<SessionInfo>,
        sendCounterpartyRequest: Boolean
    ) {
        val checkpoint = context.checkpoint

        logger.trace { "Initiating flows with sessionIds ${sessionsNotGenerated.map { it.sessionId }}" }
        // throw an error if the session already exists (shouldn't really get here for real, but for this class, it's not valid)
        val protocolStore = try {
            flowSandboxService.get(checkpoint.holdingIdentity, checkpoint.cpkFileHashes).protocolStore
        } catch (e: Exception) {
            throw FlowTransientException(
                "Failed to get the flow sandbox for identity ${checkpoint.holdingIdentity}: " +
                        (e.message?: "No exception message provided."), e
            )
        }

        val flowStack = checkpoint.flowStack
        if (flowStack.isEmpty()) {
            throw FlowFatalException("Flow stack is empty while trying to initiate a flow")
        }

        val initiatingFlowStackItem = flowStack.nearestFirst { it.isInitiatingFlow }
        val initiator = initiatingFlowStackItem?.flowName
            ?: throw FlowPlatformException("Flow stack did not contain an initiating flow in the stack")

        val (protocolName, protocolVersions) = protocolStore.protocolsForInitiator(initiator, context)

        val sessionContext = KeyValueStore().apply {
            put(FLOW_PROTOCOL, protocolName)
            put(FLOW_PROTOCOL_VERSIONS_SUPPORTED, protocolVersions.joinToString())
        }

        sessionsNotGenerated.map { sessionInfo ->
            flowSessionManager.generateSessionState(
                checkpoint,
                sessionInfo.sessionId,
                sessionInfo.counterparty,
                sessionProperties = sessionContext.apply {
                    put(FLOW_SESSION_REQUIRE_CLOSE, sessionInfo.requireClose.toString())
                    sessionInfo.sessionTimeout?.let {
                        put(FLOW_SESSION_TIMEOUT_MS, it.toMillis().toString())
                    }
                }.avro,
                Instant.now()
            ).also { checkpoint.putSessionState(it) }

            if (sendCounterpartyRequest) {
                checkpoint.putSessionState(flowSessionManager.sendCounterpartyInfoRequest(
                    context.checkpoint,
                    sessionInfo.sessionId,
                    keyValuePairListOf(sessionInfo.contextUserProperties),
                    keyValuePairListOf(sessionInfo.contextPlatformProperties),
                    sessionInfo.counterparty,
                    Instant.now()
                ))
            }
        }

        val sessionsNotInitiatedIds = sessionsNotGenerated.map { it.sessionId }.toSet()
        initiatingFlowStackItem.sessions
            .filter { it.sessionId in sessionsNotInitiatedIds }
            .forEach { it.initiated = true }
    }
}
