package net.corda.flow.pipeline.sandbox.impl

import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.CHECKPOINT_SERIALIZER
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.NON_INJECTABLE_SINGLETONS
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getSandboxSingletonServices
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * This component allows [FlowSandboxServiceImpl] to create and install a
 * [CheckpointSerializer][net.corda.serialization.checkpoint.CheckpointSerializer]
 * into the [MutableSandboxGroupContext].
 */
@Suppress("unused")
@Component(
    service = [ UsedByFlow::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = PROTOTYPE
)
class CheckpointSerializerProvider @Activate constructor(
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory,
    @Reference(service = CheckpointInternalCustomSerializer::class, scope = ReferenceScope.PROTOTYPE)
    private val checkpointInternalCustomSerializers: List<CheckpointInternalCustomSerializer<*>>
) : UsedByFlow, CustomMetadataConsumer {
    private companion object {
        private val logger = loggerFor<CheckpointSerializerProvider>()
    }

    override fun accept(context: MutableSandboxGroupContext) {
        val sandboxGroup = context.sandboxGroup

        // Get the non-injectables singletons found by FlowSandboxService.
        val nonInjectableSingletons = context.getObjectByKey<Set<SingletonSerializeAsToken>>(NON_INJECTABLE_SINGLETONS) ?: emptySet()

        // Identify all the singleton services created for this sandbox (both injectable
        // and non-injectable ones) that should be passed to the checkpoint serializer.
        val sandboxSingletons = context.getSandboxSingletonServices<SingletonSerializeAsToken>()

        // Create and configure the checkpoint serializer
        val checkpointSerializer = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxGroup).let { builder ->
            builder.addSingletonSerializableInstances(nonInjectableSingletons)
            builder.addSingletonSerializableInstances(sandboxSingletons)
            builder.addSingletonSerializableInstances(setOf(sandboxGroup))
            for (serializer in checkpointInternalCustomSerializers) {
                logger.trace("Registering internal checkpoint serializer {}", serializer::class.java.name)
                builder.addSerializer(serializer.type, serializer)
            }
            builder.build()
        }

        context.putObjectByKey(CHECKPOINT_SERIALIZER, checkpointSerializer)
    }
}
