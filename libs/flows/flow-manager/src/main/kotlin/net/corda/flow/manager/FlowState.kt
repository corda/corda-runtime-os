package net.corda.flow.manager

import net.corda.internal.application.context.InvocationContext
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.identity.Party
import net.corda.v5.base.context.Trace

class FlowState(
    val suspendCount: Int,
    val context: InvocationContext,
    val ourIdentity: Party,
    val isKilled: Boolean,
    val initiatedBy: FlowSession?,
    val sessions: MutableMap<Trace.SessionId, FlowSession>,
    val subFlows: MutableList<SubFlow>,
    val eventQueue: MutableList<FlowEvent>
)