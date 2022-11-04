package net.corda.flow.pipeline.sandbox.impl

import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.pipeline.sessions.FlowProtocolStore
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_P2P_SERIALIZATION_SERVICE
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.application.serialization.SerializationService

class FlowSandboxGroupContextImpl(
    override val dependencyInjector: SandboxDependencyInjector,
    override val checkpointSerializer: CheckpointSerializer,
    override val amqpSerializer: SerializationService,
    override val protocolStore: FlowProtocolStore,
    private val sandboxGroupContext: SandboxGroupContext
) : FlowSandboxGroupContext, SandboxGroupContext by sandboxGroupContext {

    companion object {
        const val DEPENDENCY_INJECTOR = "DEPENDENCY_INJECTOR"
        const val CHECKPOINT_SERIALIZER = "CHECKPOINT_SERIALIZER"
        const val FLOW_PROTOCOL_STORE = "FLOW_PROTOCOL_STORE"
        const val NON_INJECTABLE_SINGLETONS = "NON_INJECTABLE_SINGLETONS"

        @Suppress("ThrowsCount")
        fun fromContext(sandboxGroupContext: SandboxGroupContext): FlowSandboxGroupContext {
            val dependencyInjector = sandboxGroupContext.getObjectByKey<SandboxDependencyInjector>(DEPENDENCY_INJECTOR)
                ?: throw FlowFatalException(
                    "The flow sandbox has not been initialized with a dependency injector for " +
                            "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                )
            val checkpointSerializer = sandboxGroupContext.getObjectByKey<CheckpointSerializer>(CHECKPOINT_SERIALIZER)
                ?: throw FlowFatalException(
                    "The flow sandbox has not been initialized with a checkpoint serializer for " +
                            "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}",
                )
            val amqpSerializer =
                sandboxGroupContext.getObjectByKey<SerializationService>(AMQP_P2P_SERIALIZATION_SERVICE)
                    ?: throw FlowFatalException(
                        "The flow sandbox has not been initialized with an AMQP serializer for " +
                                "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                    )
            val protocolStore = sandboxGroupContext.getObjectByKey<FlowProtocolStore>(FLOW_PROTOCOL_STORE)
                ?: throw FlowFatalException(
                    "The flow sandbox has not been initialized with a protocol store for " +
                            "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                )
            return FlowSandboxGroupContextImpl(
                dependencyInjector,
                checkpointSerializer,
                amqpSerializer,
                protocolStore,
                sandboxGroupContext
            )
        }
    }
}
