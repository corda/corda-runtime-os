package net.corda.flow.mapper.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.flow.mapper.factory.FlowMapperMetaDataFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Component

@Component(service = [FlowMapperMetaDataFactory::class])
class FlowMapperMetaDataFactoryImpl : FlowMapperMetaDataFactory {

    override fun createFromEvent(
        flowMapperTopics: FlowMapperTopics, state: FlowMapperState?, eventRecord: Record<String, FlowMapperEvent>
    ): FlowMapperMetaData {
        val flowEvent = eventRecord.value
        val payload = flowEvent?.payload ?: throw CordaRuntimeException("No payload for session event on key ${eventRecord.key}")
        var holdingIdentity: HoldingIdentity? = null

        var outputTopic: String? = null
        var expiryTime: Long? = null
        val messageDirection = flowEvent.messageDirection

        when (payload) {
            is StartRPCFlow -> {
                holdingIdentity = payload.rpcUsername
                outputTopic = flowMapperTopics.flowEventTopic
            }
            is ScheduleCleanup -> {
                outputTopic = flowMapperTopics.flowMapperEventTopic
                expiryTime = payload.expiryTime
            }
            is ExecuteCleanup -> {
                expiryTime = state?.expiryTime
            }
            is SessionEvent -> {
                outputTopic = getSessionEventOutputTopic(flowMapperTopics, flowEvent.messageDirection)
                val sessionPayload = payload.payload

                if (sessionPayload is SessionInit) {
                    holdingIdentity = if (messageDirection == MessageDirection.OUTBOUND) {
                        sessionPayload.flowKey.identity
                    } else {
                        sessionPayload.initiatedIdentity
                    }
                }
            }
        }

        return FlowMapperMetaData(
            flowMapperEvent = flowEvent,
            flowMapperEventKey = eventRecord.key,
            outputTopic = outputTopic,
            holdingIdentity = holdingIdentity,
            payload = payload,
            flowMapperState = state,
            messageDirection = messageDirection,
            expiryTime = expiryTime
        )
    }

    /**
     * Get the output topic based on [messageDirection].
     * Inbound records should be directed to the flow event topic.
     * Outbound records should be directed to the p2p out topic.
     */
    private fun getSessionEventOutputTopic(flowMapperTopics: FlowMapperTopics, messageDirection: MessageDirection): String {
        return if (messageDirection == MessageDirection.INBOUND) {
            flowMapperTopics.flowEventTopic
        } else {
            flowMapperTopics.p2pOutTopic
        }
    }
}
