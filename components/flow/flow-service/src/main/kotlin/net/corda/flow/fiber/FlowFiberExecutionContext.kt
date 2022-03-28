package net.corda.flow.fiber

import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.utils.getMemberX500Name
import net.corda.membership.read.MembershipGroupReader
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.base.types.MemberX500Name

@Suppress("LongParameterList")
class FlowFiberExecutionContext(
    val sandboxDependencyInjector: SandboxDependencyInjector,
    val flowStackService: FlowStackService,
    val checkpointSerializer: CheckpointSerializer,
    val sandboxGroupContext: SandboxGroupContext,
    val holdingIdentity: HoldingIdentity,
    val membershipGroupReader: MembershipGroupReader
) : NonSerializable{
    val memberX500Name:MemberX500Name = holdingIdentity.getMemberX500Name()
}

