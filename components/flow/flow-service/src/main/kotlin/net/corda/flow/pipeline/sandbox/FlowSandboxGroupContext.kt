package net.corda.flow.pipeline.sandbox

import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector
import net.corda.serialization.checkpoint.CheckpointSerializer

/**
 * A sandbox group context decorated with extra services required by the flow pipeline.
 */
interface FlowSandboxGroupContext: SandboxGroupContext {

    val dependencyInjector: SandboxDependencyInjector

    val checkpointSerializer: CheckpointSerializer

    val protocolStore: FlowProtocolStore
}
