package net.corda.flow.mapper

/**
 * input and output topics of the Flow Mapper
 */
data class FlowMapperTopics(
    val p2pOutTopic: String,
    val flowMapperEventTopic: String,
    val flowEventTopic: String
)
