package net.corda.flow.fiber

import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.read.MembershipGroupReader
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.tracing.TraceContext
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

@Suppress("LongParameterList")
class FlowFiberExecutionContext(
    val flowCheckpoint: FlowCheckpoint,
    val sandboxGroupContext: FlowSandboxGroupContext,
    val holdingIdentity: HoldingIdentity,
    val membershipGroupReader: MembershipGroupReader,
    val currentSandboxGroupContext: CurrentSandboxGroupContext,
    val mdcLoggingData: Map<String, String>,
    val flowMetrics: FlowMetrics,
    val configs: Map<String, SmartConfig>,
    val traceContext: TraceContext
) : NonSerializable {
    val memberX500Name: MemberX500Name get() = holdingIdentity.x500Name
    val flowStackService: FlowStack get() = flowCheckpoint.flowStack
}
