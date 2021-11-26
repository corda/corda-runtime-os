package net.corda.flow.manager

import net.corda.data.flow.FlowInfo

data class FlowMetaData(
    val flowName: String,
    val flowInfo: FlowInfo,
    val jsonArg: String,
    val cpiId: String,
    val flowEventTopic: String,
)
