package net.corda.flow.mapper

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.identity.HoldingIdentity

/**
 * Class to encapsulate data about different FLow Mapper Events
 */
data class FlowMapperMetaData (
    val flowMapperEvent: FlowMapperEvent,
    val flowMapperEventKey: String,
    val outputTopic: String?,
    val holdingIdentity: HoldingIdentity?,
    val payload: Any,
    val flowMapperState: FlowMapperState?,
    val messageDirection: MessageDirection?,
    val expiryTime: Long?,
)
