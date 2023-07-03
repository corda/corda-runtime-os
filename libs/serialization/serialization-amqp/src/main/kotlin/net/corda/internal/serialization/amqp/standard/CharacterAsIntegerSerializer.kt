package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers.primitiveTypeName
import net.corda.internal.serialization.amqp.CastingSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Serializer for a field which is declared as [Char] but where the
 * actual type is [Int]. Most probably just used for deserializing.
 */
class CharacterAsIntegerSerializer : CastingSerializer<Char, Int> {
    override val type: Class<Char>
        get() = Char::class.javaObjectType

    override val actualType: Class<Int>
        get() = Int::class.javaObjectType

    override val typeDescriptor: Symbol = Symbol.valueOf(primitiveTypeName(type))

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {
    }

    override fun writeObject(
        obj: Any,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext,
        debugIndent: Int
    ) {
        data.putChar(obj as Int)
    }

    override fun readObject(
        obj: Any,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        input: DeserializationInput,
        context: SerializationContext
    ): Char {
        return (obj as Int).toChar()
    }
}
