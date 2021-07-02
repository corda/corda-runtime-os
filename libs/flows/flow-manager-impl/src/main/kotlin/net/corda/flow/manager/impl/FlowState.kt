package net.corda.flow.manager.impl

import net.corda.flow.manager.FlowEvent
import net.corda.flow.manager.FlowSession
import net.corda.flow.manager.SubFlow
import net.corda.internal.application.context.InvocationContext
import net.corda.v5.application.identity.Party
import net.corda.v5.base.context.Trace

@Suppress("LongParameterList")
class FlowState(
    val suspendCount: Int,
    val context: InvocationContext,
    val ourIdentity: Party,
    val isKilled: Boolean,
    val initiatedBy: FlowSessionImpl?,
    val sessions: MutableMap<Trace.SessionId, FlowSession>,
    val subFlows: MutableList<SubFlow>,
    val eventQueue: MutableList<FlowEvent>
)