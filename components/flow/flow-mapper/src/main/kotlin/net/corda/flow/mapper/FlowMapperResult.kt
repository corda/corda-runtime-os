package net.corda.flow.mapper

import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.messaging.api.records.Record

data class FlowMapperResult(
    val flowMapperState: FlowMapperState?,
    val outputEvents: List<Record<*, *>>
)
