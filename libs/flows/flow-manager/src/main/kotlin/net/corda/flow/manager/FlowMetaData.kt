package net.corda.flow.manager

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.packaging.CPI
import net.corda.virtualnode.HoldingIdentity

interface FlowMetaData {
    val flowEvent: FlowEvent
    val clientId: String
    val flowName: String
    val flowKey: FlowKey
    val jsonArg: String
    val cpiId: String
    val flowEventTopic: String
    val holdingIdentity: HoldingIdentity
    val cpi: CPI.Identifier
    val payload: Any
    val checkpoint: Checkpoint?
}

