package net.corda.sandbox.serialization.amqp

import java.util.function.Supplier
import net.corda.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_SERIALIZER_FACTORY
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SerializationCustomSerializer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope

/**
 * Configures a sandbox with AMQP serialization support.
 */
@Suppress("unused")
@Component(
    service = [ UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = ServiceScope.PROTOTYPE
)
class AMQPSerializationProvider @Activate constructor(
    @Reference(service = InternalCustomSerializer::class, scope = PROTOTYPE)
    private val internalSerializers: List<InternalCustomSerializer<out Any>>,
    @Reference(service = SerializationServiceProxy::class, scope = PROTOTYPE_REQUIRED, cardinality = OPTIONAL)
    private val serializationServiceProxy: SerializationServiceProxy?
) : UsedByFlow, UsedByPersistence, UsedByVerification, CustomMetadataConsumer {
    private companion object {
        private val logger = loggerFor<AMQPSerializationProvider>()
    }

    private fun createSerializerFactory(context: SandboxGroupContext): SerializerFactory {
        val factory = SerializerFactoryBuilder.build(context.sandboxGroup)

        registerCustomSerializers(factory)

        for (internalSerializer in internalSerializers) {
            logger.trace("Registering internal serializer {}", internalSerializer::class.java.name)
            factory.register(internalSerializer, factory)
        }

        context.getMetadataServices<SerializationCustomSerializer<*,*>>().forEach { customSerializer ->
            // Register CorDapp serializers
            logger.trace("Registering CorDapp serializer {}", customSerializer::class.java.name)
            factory.registerExternal(customSerializer, factory)
        }

        return factory
    }

    override fun accept(context: MutableSandboxGroupContext) {
        val factory = createSerializerFactory(context)

        val serializationOutput = SerializationOutput(factory)
        val deserializationInput = DeserializationInput(factory)

        val serializationService = SerializationServiceImpl(
            serializationOutput,
            deserializationInput,
            AMQP_P2P_CONTEXT.withSandboxGroup(context.sandboxGroup)
        )

        context.putObjectByKey(AMQP_SERIALIZATION_SERVICE, serializationService)
        serializationServiceProxy?.wrap(serializationService)

        // Support creating other serializer factories for this sandbox (e.g. for testing)
        context.putObjectByKey(AMQP_SERIALIZER_FACTORY, Supplier { createSerializerFactory(context) })
    }
}
