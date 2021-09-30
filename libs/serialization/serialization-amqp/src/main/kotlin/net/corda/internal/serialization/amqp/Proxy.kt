package net.corda.internal.serialization.amqp

import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Base features for when the serialized form should be the serialized form of a proxy class, and the object can
 * be re-created from that proxy on deserialization.
 *
 * The proxy class must use only types which are either native AMQP or other types for which there are pre-registered
 * custom serializers.
 */
abstract class Proxy<T : Any, P : Any>(
    clazz: Class<T>,
    protected val proxyClass: Class<P>,
    protected val factory: LocalSerializerFactory,
    withInheritance: Boolean = true
) : CustomSerializerImp<T>(clazz, withInheritance) {
    override fun isSerializerFor(clazz: Class<*>): Boolean =
        if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz

    private val proxySerializer: ObjectSerializer by lazy {
        ObjectSerializer.make(
            factory.getTypeInformation(
                proxyClass
            ), factory
        )
    }

    override val schemaForDocumentation: Schema by lazy {
        val typeNotations = mutableSetOf<TypeNotation>(
            CompositeType(
                AMQPTypeIdentifiers.nameForType(type),
                null,
                emptyList(),
                descriptor, proxySerializer.fields
            )
        )
        Schema(typeNotations.toList())
    }

    /**
     * Implement these two methods.
     */
    protected abstract fun toProxy(obj: T): P

    protected abstract fun fromProxy(proxy: P): T

    override fun writeDescribedObject(
        obj: T, data: Data, type: Type, output: SerializationOutput,
        context: SerializationContext
    ) {
        val proxy = toProxy(obj)
        data.withList {
            proxySerializer.propertySerializers.forEach { (_, serializer) ->
                serializer.writeProperty(proxy, this, output, context, 0)
            }
        }
    }

    override fun readObject(
        obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
        input: DeserializationInput, context: SerializationContext
    ): T {
        val proxy: P =
            uncheckedCast(proxySerializer.readObject(obj, serializationSchemas, metadata, input, context))
        return fromProxy(proxy)
    }
}