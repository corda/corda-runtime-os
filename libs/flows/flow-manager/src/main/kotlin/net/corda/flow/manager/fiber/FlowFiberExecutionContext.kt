package net.corda.flow.manager.fiber

import net.corda.flow.manager.FlowStackService
import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.data.identity.HoldingIdentity

class FlowFiberExecutionContext(
    val sandboxDependencyInjector: SandboxDependencyInjector,
    val flowStackService: FlowStackService,
    val checkpointSerializer: CheckpointSerializer,
    val sandboxGroupContext: SandboxGroupContext,
    val holdingIdentity: HoldingIdentity
): NonSerializable

