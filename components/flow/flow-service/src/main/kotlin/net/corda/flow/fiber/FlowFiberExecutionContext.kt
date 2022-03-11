package net.corda.flow.fiber

import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.serialization.checkpoint.NonSerializable

class FlowFiberExecutionContext(
    val sandboxDependencyInjector: SandboxDependencyInjector,
    val flowStackService: FlowStackService,
    val checkpointSerializer: CheckpointSerializer,
    val sandboxGroupContext: SandboxGroupContext,
    val holdingIdentity: HoldingIdentity
): NonSerializable

