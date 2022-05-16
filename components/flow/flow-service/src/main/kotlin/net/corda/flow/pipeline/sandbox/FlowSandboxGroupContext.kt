package net.corda.flow.pipeline.sandbox

import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sandbox.FlowSandboxSerializerTypes.AMQP_P2P_SERIALIZATION_SERVICE
import net.corda.flow.pipeline.sessions.FlowProtocolStore
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getUniqueObject
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.application.serialization.SerializationService

/**
 * A sandbox group context decorated with extra services required by the flow pipeline.
 */
class FlowSandboxGroupContext(
    val dependencyInjector: SandboxDependencyInjector,
    val checkpointSerializer: CheckpointSerializer,
    val amqpSerializer: SerializationService,
    val protocolStore: FlowProtocolStore,
    private val sandboxGroupContext: SandboxGroupContext
) : SandboxGroupContext by sandboxGroupContext {

    companion object {
        fun fromContext(sandboxGroupContext: SandboxGroupContext) : FlowSandboxGroupContext {
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
            val amqpSerializer = sandboxGroupContext.getObjectByKey<SerializationService>(AMQP_P2P_SERIALIZATION_SERVICE)
                ?: throw FlowProcessingException(
                    "The flow sandbox has not been initialized with an AMQP serializer for " +
                            "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
                )
            val protocolStore = sandboxGroupContext.getUniqueObject<FlowProtocolStore>()
            ?: throw FlowProcessingException(
                "The flow sandbox has not been initialized with a protocol store for " +
                        "identity ${sandboxGroupContext.virtualNodeContext.holdingIdentity}"
            )
            return FlowSandboxGroupContext(
                dependencyInjector,
                checkpointSerializer,
                amqpSerializer,
                protocolStore,
                sandboxGroupContext
            )
        }
    }
}