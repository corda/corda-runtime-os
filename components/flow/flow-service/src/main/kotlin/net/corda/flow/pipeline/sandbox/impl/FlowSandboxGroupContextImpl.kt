package net.corda.flow.pipeline.sandbox.impl

import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxSerializerTypes
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.pipeline.sessions.FlowProtocolStore
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getUniqueObject
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
        fun fromContext(sandboxGroupContext: SandboxGroupContext): FlowSandboxGroupContext {
            val dependencyInjector = sandboxGroupContext.getUniqueObject<SandboxDependencyInjector>()
                ?: throw FlowProcessingException(
                    "The flow sandbox has not been initialized with a dependency injector for " +
                            "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                )
            val checkpointSerializer = sandboxGroupContext.getUniqueObject<CheckpointSerializer>()
                ?: throw FlowProcessingException(
                    "The flow sandbox has not been initialized with a checkpoint serializer for " +
                            "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                )
            val amqpSerializer =
                sandboxGroupContext.getObjectByKey<SerializationService>(FlowSandboxSerializerTypes.AMQP_P2P_SERIALIZATION_SERVICE)
                    ?: throw FlowProcessingException(
                        "The flow sandbox has not been initialized with an AMQP serializer for " +
                                "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                    )
            val protocolStore = sandboxGroupContext.getUniqueObject<FlowProtocolStore>()
                ?: throw FlowProcessingException(
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