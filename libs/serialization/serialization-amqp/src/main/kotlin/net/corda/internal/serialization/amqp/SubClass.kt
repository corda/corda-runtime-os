package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.model.FingerprintWriter
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * This custom serializer represents a sort of symbolic link from a subclass to a super class, where the super
 * class custom serializer is responsible for the "on the wire" format but we want to create a reference to the
 * subclass in the schema, so that we can distinguish between subclasses.
 */
class SubClass<T : Any>(private val clazz: Class<*>, private val superClassSerializer: CorDappCustomSerializer) :
    AMQPSerializer<T> {

    override val type: Type get() = clazz

    override val typeDescriptor: Symbol by lazy {
        val fingerprint = FingerprintWriter()
            .write(superClassSerializer.typeDescriptor)
            .write(AMQPTypeIdentifiers.nameForType(clazz))
            .fingerprint
        Symbol.valueOf("$DESCRIPTOR_DOMAIN:$fingerprint")
    }

    private val typeNotation: TypeNotation = RestrictedType(
        AMQPTypeIdentifiers.nameForType(clazz),
        null,
        emptyList(),
        AMQPTypeIdentifiers.nameForType(superClassSerializer.type),
        Descriptor(typeDescriptor),
        emptyList()
    )

    override fun writeClassInfo(output: SerializationOutput) {
        output.writeTypeNotations(typeNotation)
    }

    val descriptor: Descriptor = Descriptor(typeDescriptor)

    override fun writeObject(
        obj: Any,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext,
        debugIndent: Int
    ) {
        data.withDescribed(descriptor) {
            superClassSerializer.writeObject(obj, data, type, output, context)
        }
    }

    override fun readObject(
        obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
        input: DeserializationInput, context: SerializationContext
    ): T {
        return uncheckedCast(superClassSerializer.readObject(obj, serializationSchemas, metadata, input, context))
    }
}