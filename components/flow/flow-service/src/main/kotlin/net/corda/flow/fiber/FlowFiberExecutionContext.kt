package net.corda.flow.fiber

import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.membership.read.MembershipGroupReader
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

class FlowFiberExecutionContext(
    val flowCheckpoint: FlowCheckpoint,
    val sandboxGroupContext: FlowSandboxGroupContext,
    val holdingIdentity: HoldingIdentity,
    val membershipGroupReader: MembershipGroupReader,
    val mdcLoggingData: Map<String, String>
) : NonSerializable {
    val memberX500Name: MemberX500Name = holdingIdentity.x500Name
    val flowStackService: FlowStack = flowCheckpoint.flowStack
}

