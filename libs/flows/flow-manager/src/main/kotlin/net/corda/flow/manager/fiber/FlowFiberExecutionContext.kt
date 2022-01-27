package net.corda.flow.manager.fiber

import net.corda.flow.manager.FlowStackService
import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.serialization.CheckpointSerializer
import net.corda.serialization.NonSerializable

class FlowFiberExecutionContext(
    val sandboxDependencyInjector: SandboxDependencyInjector,
    val flowStackService: FlowStackService,
    val checkpointSerializer: CheckpointSerializer,
    val sandboxGroupContext: SandboxGroupContext
): NonSerializable

