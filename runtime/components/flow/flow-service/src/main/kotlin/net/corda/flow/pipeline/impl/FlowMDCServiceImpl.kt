package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.pipeline.FlowMDCService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [FlowMDCService::class])
class FlowMDCServiceImpl : FlowMDCService {
    
    private companion object {
        const val MDC_FLOW_ID = "flow_id"
        const val MDC_CLIENT_ID = "client_id"
        const val MDC_VNODE_ID = "vnode_id"
        const val MDC_SESSION_EVENT_ID = "session_event_id"
        const val MDC_EXTERNAL_EVENT_ID = "external_event_id"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    
    override fun getMDCLogging(checkpoint: Checkpoint?, event: FlowEvent?, flowId: String): Map<String, String> {
        return if (checkpoint != null) {
            getMDCFromCheckpoint(checkpoint, event, flowId)
        } else {
            getMDCFromEvent(event, flowId)
        }
    }

    /**
     * Extract out the MDC logging info from a [flowEvent]. The flow event is expected to be of type [StartFlow] or a [SessionInit].
     */
    private fun getMDCFromEvent(flowEvent: FlowEvent?, flowId: String): Map<String, String> {
        return when (val payload = flowEvent?.payload) {
            is StartFlow -> {
                val startContext = payload.startContext
                val startKey = startContext.statusKey
                val holdingIdentityShortHash = startKey.identity.toCorda().shortHash.toString()
                mapOf(
                    MDC_VNODE_ID to holdingIdentityShortHash,
                    MDC_CLIENT_ID to startContext.requestId,
                    MDC_FLOW_ID to flowId
                )
            }
            is SessionEvent -> {
                //no checkpoint so this is either a SessionInit or a duplicate SessionEvent for an expired session
                val holdingIdentityShortHash = payload.initiatedIdentity.toCorda().shortHash.toString()
                mapOf(MDC_VNODE_ID to holdingIdentityShortHash,
                    MDC_CLIENT_ID to payload.sessionId,
                    MDC_SESSION_EVENT_ID to payload.sessionId,
                    MDC_FLOW_ID to flowId
                )
            }
            else -> {
                //this shouldn't happen
                val payloadType = if (payload != null) payload::class else null
                logger.warn("Failed to set MDC. Flow event (id: $flowId) with null state " +
                        "where event payload is of type $payloadType. Payload: $payload")
                mapOf(MDC_FLOW_ID to flowId)
            }
        }
    }

    /**
     * Extract out the MDC logging info from the [checkpoint].
     */
    private fun getMDCFromCheckpoint(state: Checkpoint, event: FlowEvent?, flowId: String): Map<String, String> {
        val flowState = state.flowState ?: return emptyMap()
        val startContext = flowState.flowStartContext
        val vNodeShortHash = startContext.identity.toCorda().shortHash.toString()
        val mdcLogging = mutableMapOf(
            MDC_VNODE_ID to vNodeShortHash,
            MDC_CLIENT_ID to startContext.requestId,
            MDC_FLOW_ID to flowId
        )
        setExternalEventIdIfNotComplete(flowState, mdcLogging)
        setSessionMDCFromEvent(event, mdcLogging)
        return mdcLogging
    }

    private fun setSessionMDCFromEvent(event: FlowEvent?, mdcLogging: MutableMap<String, String>) {
        val payload = event?.payload ?: return
        if (payload is SessionEvent) {
            mdcLogging[MDC_SESSION_EVENT_ID] = payload.sessionId
        }
    }

    /**
     * If a response has not been received from the external event or if it is still retrying a request then set the external event id
     * into the MDC.
     */
    private fun setExternalEventIdIfNotComplete(
        flowState: FlowState,
        mdcLogging: MutableMap<String, String>
    ) {
        val extState = flowState.externalEventState
        if (extState != null) {
            val status = extState.status
            if (extState.response == null || status.type == ExternalEventStateType.RETRY) {
                mdcLogging[MDC_EXTERNAL_EVENT_ID] = extState.requestId
            }
        }
    }
}