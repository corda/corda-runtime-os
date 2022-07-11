package net.corda.flow.fiber

import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.membership.read.MembershipGroupReader
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

@Suppress("LongParameterList")
class FlowFiberExecutionContext(
    val flowCheckpoint: FlowCheckpoint,
    val sandboxGroupContext: FlowSandboxGroupContext,
    val holdingIdentity: HoldingIdentity,
    val membershipGroupReader: MembershipGroupReader
) : NonSerializable {
    val memberX500Name: MemberX500Name = holdingIdentity.getMemberX500Name()
    val flowStackService: FlowStack = flowCheckpoint.flowStack

    private fun HoldingIdentity.getMemberX500Name() : MemberX500Name {
        try {
            return MemberX500Name.parse(x500Name)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to convert Holding Identity x500 name '${x500Name}' to MemberX500Name",
                e
            )
        }
    }
}

