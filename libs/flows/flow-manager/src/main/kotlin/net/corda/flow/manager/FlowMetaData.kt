package net.corda.flow.manager

import net.corda.data.flow.FlowKey

data class FlowMetaData(
    val flowName: String,
    val flowKey: FlowKey,
    val jsonArg: String,
)
