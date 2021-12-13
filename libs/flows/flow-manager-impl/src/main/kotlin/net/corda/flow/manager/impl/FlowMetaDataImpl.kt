package net.corda.flow.manager.impl


import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowMetaData
import net.corda.packaging.CPI
import net.corda.virtualnode.HoldingIdentity

data class FlowMetaDataImpl(
    override val flowEvent: FlowEvent,
    override val clientId: String,
    override val flowName: String,
    override val flowKey: FlowKey,
    override val jsonArg: String,
    override val cpiId: String,
    override val flowEventTopic: String,
    override val holdingIdentity: HoldingIdentity,
    override val cpi: CPI.Identifier,
    override val payload: Any,
    override val checkpoint: Checkpoint?
) : FlowMetaData