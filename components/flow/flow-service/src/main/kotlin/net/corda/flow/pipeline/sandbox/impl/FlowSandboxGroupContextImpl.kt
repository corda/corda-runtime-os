package net.corda.flow.pipeline.sandbox.impl

import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.application.flows.Flow

class FlowSandboxGroupContextImpl(
    override val dependencyInjector: SandboxDependencyInjector<Flow>,
    override val checkpointSerializer: CheckpointSerializer,
    override val protocolStore: FlowProtocolStore,
    override val fiberCache: FlowFiberCache,
    private val sandboxGroupContext: SandboxGroupContext,
) : FlowSandboxGroupContext, SandboxGroupContext by sandboxGroupContext {

    companion object {
        const val DEPENDENCY_INJECTOR = "DEPENDENCY_INJECTOR"
        const val CHECKPOINT_SERIALIZER = "CHECKPOINT_SERIALIZER"
        const val FLOW_PROTOCOL_STORE = "FLOW_PROTOCOL_STORE"
        const val NON_INJECTABLE_SINGLETONS = "NON_INJECTABLE_SINGLETONS"
        const val FLOW_FIBER_CACHE = "FLOW_FIBER_CACHE"

        @Suppress("ThrowsCount")
        fun fromContext(sandboxGroupContext: SandboxGroupContext): FlowSandboxGroupContext {
            val dependencyInjector =
                sandboxGroupContext.getObjectByKey<SandboxDependencyInjector<Flow>>(DEPENDENCY_INJECTOR)
                    ?: throw FlowFatalException(
                        "The flow sandbox has not been initialized with a dependency injector for " +
                            "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                    )
            val checkpointSerializer = sandboxGroupContext.getObjectByKey<CheckpointSerializer>(CHECKPOINT_SERIALIZER)
                ?: throw FlowFatalException(
                    "The flow sandbox has not been initialized with a checkpoint serializer for " +
                        "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}",
                )
            val protocolStore = sandboxGroupContext.getObjectByKey<FlowProtocolStore>(FLOW_PROTOCOL_STORE)
                ?: throw FlowFatalException(
                    "The flow sandbox has not been initialized with a protocol store for " +
                        "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                )
            val fiberCache = sandboxGroupContext.getObjectByKey<FlowFiberCache>(FLOW_FIBER_CACHE)
                ?: throw FlowFatalException(
                    "The flow sandbox has not been initialized with a fiber cache for " +
                        "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                )
            return FlowSandboxGroupContextImpl(
                dependencyInjector,
                checkpointSerializer,
                protocolStore,
                fiberCache,
                sandboxGroupContext
            )
        }
    }
}
