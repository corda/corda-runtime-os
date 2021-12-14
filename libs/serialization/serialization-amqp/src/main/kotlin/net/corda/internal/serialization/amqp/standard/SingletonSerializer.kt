package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.Descriptor
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * A custom serializer that transports nothing on the wire (except a boolean "false", since AMQP does not support
 * absolutely nothing, or null as a described type) when we have a singleton within the node that we just
 * want converting back to that singleton instance on the receiving JVM.
 */
class SingletonSerializer(override val type: Class<*>, val singleton: Any, factory: LocalSerializerFactory) :
    AMQPSerializer<Any> {
    override val typeDescriptor = factory.createDescriptor(type)

    private val interfaces = (factory.getTypeInformation(type) as LocalTypeInformation.Singleton).interfaces

    private fun generateProvides(): List<String> = interfaces.map { it.typeIdentifier.name }

    internal val typeNotation: TypeNotation = RestrictedType(
        type.typeName,
        "Singleton",
        generateProvides(),
        "boolean",
        Descriptor(typeDescriptor),
        emptyList()
    )

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {
        output.writeTypeNotations(typeNotation)
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        data.withDescribed(typeNotation.descriptor) {
            data.putBoolean(false)
        }
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext
    ): Any {
        return singleton
    }
}