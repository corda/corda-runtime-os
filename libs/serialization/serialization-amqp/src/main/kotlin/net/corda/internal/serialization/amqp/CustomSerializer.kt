package net.corda.internal.serialization.amqp

import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Base class for serializers of core platform types that do not conform to the usual serialization rules and thus
 * cannot be automatically serialized.
 */
abstract class CustomSerializer<T : Any> : AMQPSerializer<T>, SerializerFor {

    protected abstract val descriptor: Descriptor

    /**
     * This exists purely for documentation and cross-platform purposes. It is not used by our serialization / deserialization
     * code path.
     */
    abstract val schemaForDocumentation: Schema

    /**
     * Whether subclasses using this serializer via inheritance should have a mapping in the schema.
     */
    override val revealSubclassesInSchema: Boolean get() = false

    override fun writeObject(
        obj: Any,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext,
        debugIndent: Int
    ) {
        data.withDescribed(descriptor) {
            writeDescribedObject(uncheckedCast(obj), data, type, output, context)
        }
    }

    abstract fun writeDescribedObject(
        obj: T,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext
    )

    /**
     * [CustomSerializerRegistry.findCustomSerializer] will invoke this method on the [CustomSerializer]
     * that it selects to give that serializer an opportunity to customise its behaviour. The serializer
     * can also return `null` here, in which case [CustomSerializerRegistry] will proceed as if no
     * serializer is available for [declaredType].
     */
    open fun specialiseFor(declaredType: Type): AMQPSerializer<T>? = this
}
