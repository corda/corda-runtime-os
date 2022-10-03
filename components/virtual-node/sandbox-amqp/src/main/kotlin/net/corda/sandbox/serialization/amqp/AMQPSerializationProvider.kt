package net.corda.sandbox.serialization.amqp

import net.corda.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_P2P_SERIALIZATION_SERVICE
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SerializationCustomSerializer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * Configures a sandbox with AMQP serialization support.
 */
@Suppress("unused")
@Component(
    service = [ UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    property = [ "corda.marker.only:Boolean=true" ],
    scope = PROTOTYPE
)
class AMQPSerializationProvider @Activate constructor(
    @Reference(service = InternalCustomSerializer::class)
    private val internalSerializers: List<InternalCustomSerializer<out Any>>,
    @Reference(service = SerializationServiceProxy::class, scope = PROTOTYPE_REQUIRED, cardinality = OPTIONAL)
    private val serializationServiceProxy: SerializationServiceProxy?
) : UsedByFlow, UsedByPersistence, UsedByVerification, CustomMetadataConsumer {
    private companion object {
        private val CORDAPP_CUSTOM_SERIALIZER = SerializationCustomSerializer::class.java
        private val logger = loggerFor<AMQPSerializationProvider>()
    }

    override fun accept(context: MutableSandboxGroupContext) {
        val sandboxGroup = context.sandboxGroup
        val factory = SerializerFactoryBuilder.build(sandboxGroup)

        registerCustomSerializers(factory)

        for (internalSerializer in internalSerializers) {
            logger.trace("Registering internal serializer {}", internalSerializer::class.java.name)
            factory.register(internalSerializer, factory)
        }

        // Build CorDapp serializers
        // Current implementation has unique serializers per CPI
        context.getObjectByKey<Iterable<SerializationCustomSerializer<*, *>>>(CORDAPP_CUSTOM_SERIALIZER.name)?.forEach { customSerializer ->
            // Register CorDapp serializers
            logger.trace("Registering CorDapp serializer {}", customSerializer::class.java.name)
            factory.registerExternal(customSerializer, factory)
        }

        val serializationOutput = SerializationOutput(factory)
        val deserializationInput = DeserializationInput(factory)

        val p2pSerializationService = SerializationServiceImpl(
            serializationOutput,
            deserializationInput,
            AMQP_P2P_CONTEXT.withSandboxGroup(sandboxGroup)
        )

        context.putObjectByKey(AMQP_P2P_SERIALIZATION_SERVICE, p2pSerializationService)
        serializationServiceProxy?.wrap(p2pSerializationService)
    }
}
