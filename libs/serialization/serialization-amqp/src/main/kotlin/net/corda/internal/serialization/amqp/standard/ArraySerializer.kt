package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.Descriptor
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.asClass
import net.corda.internal.serialization.amqp.componentType
import net.corda.internal.serialization.amqp.isArray
import net.corda.internal.serialization.amqp.redescribe
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.withList
import net.corda.internal.serialization.model.resolveAgainst
import net.corda.serialization.SerializationContext
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * Serialization / deserialization of arrays.
 */
open class ArraySerializer(override val type: Type, factory: LocalSerializerFactory) : AMQPSerializer<Any> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        fun make(type: Type, factory: LocalSerializerFactory) : AMQPSerializer<Any> {
            logger.debug { "Making array serializer, typename=${type.typeName}" }
            return when (type) {
                Array<Char>::class.java -> CharArraySerializer(factory)
                else -> ArraySerializer(type, factory)
            }
        }
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    // because this might be an array of array of primitives (to any recursive depth) and
    // because we care that the lowest type is unboxed we can't rely on the inbuilt type
    // id to generate it properly (it will always return [[[Ljava.lang.type -> type[][][]
    // for example).
    //
    // We *need* to retain knowledge for AMQP deserialization whether that lowest primitive
    // was boxed or unboxed so just infer it recursively.
    private fun calcTypeName(type: Type, debugOffset : Int = 0): String {
        logger.trace { "${"".padStart(debugOffset, ' ') }  calcTypeName - ${type.typeName}" }

        val componentType = type.componentType()
        return if (componentType.isArray()) {
            // Special case handler for primitive byte arrays. This is needed because we can silently
            // coerce a byte[] to our own binary type. Normally, if the component type was itself an
            // array we'd keep walking down the chain but for byte[] stop here and use binary instead
            val typeName =  if (AMQPTypeIdentifiers.isPrimitive(componentType)) {
                AMQPTypeIdentifiers.nameForType(componentType)
            } else {
                calcTypeName(componentType, debugOffset + 4)
            }

            "$typeName[]"
        } else {
            val arrayType = if (type.asClass().componentType.isPrimitive) "[p]" else "[]"
            "${componentType.typeName}$arrayType"
        }
    }

    override val typeDescriptor: Symbol by lazy(PUBLICATION) {
        factory.createDescriptor(type)
    }

    internal val elementType: Type by lazy(PUBLICATION) { type.componentType().resolveAgainst(type) }
    internal open val typeName by lazy(PUBLICATION) { calcTypeName(type) }

    internal val typeNotation: TypeNotation by lazy(PUBLICATION) {
        RestrictedType(typeName, null, emptyList(), "list", Descriptor(typeDescriptor), emptyList())
    }

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(elementType, context)
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            withList {
                for (entry in obj as Array<*>) {
                    output.writeObjectOrNull(entry, this, elementType, context, debugIndent)
                }
            }
        }
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext
    ): Any {
        if (obj is List<*>) {
            return obj.map {
                input.readObjectOrNull(redescribe(it, elementType), serializationSchemas, metadata, elementType, context)
            }.toArrayOfType(elementType)
        } else throw AMQPNotSerializableException(type, "Expected a List but found $obj")
    }

    open fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType = type.asClass()
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            (0..lastIndex).forEach { java.lang.reflect.Array.set(this, it, list[it]) }
        }
    }
}

// Boxed Character arrays required a specialisation to handle the type conversion properly when populating
// the array since Kotlin won't allow an implicit cast from Int (as they're stored as 16bit ints) to Char
class CharArraySerializer(factory: LocalSerializerFactory) : ArraySerializer(Array<Char>::class.java, factory) {
    override fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType = type.asClass()
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            (0..lastIndex).forEach { java.lang.reflect.Array.set(this, it, (list[it] as Int).toChar()) }
        }
    }
}

// Specialisation of [ArraySerializer] that handles arrays of unboxed java primitive types
abstract class PrimArraySerializer(type: Type, factory: LocalSerializerFactory) : ArraySerializer(type, factory) {
    companion object {
        // We don't need to handle the unboxed byte type as that is coercible to a byte array, but
        // the other 7 primitive types we do
        private val primTypes: Map<Type, (LocalSerializerFactory) -> PrimArraySerializer> = mapOf(
                IntArray::class.java to { f -> PrimIntArraySerializer(f) },
                CharArray::class.java to { f -> PrimCharArraySerializer(f) },
                BooleanArray::class.java to { f -> PrimBooleanArraySerializer(f) },
                FloatArray::class.java to { f -> PrimFloatArraySerializer(f) },
                ShortArray::class.java to { f -> PrimShortArraySerializer(f) },
                DoubleArray::class.java to { f -> PrimDoubleArraySerializer(f) },
                LongArray::class.java to { f -> PrimLongArraySerializer(f) }
                // ByteArray::class.java <-> NOT NEEDED HERE (see comment above)
        )

        fun make(type: Type, factory: LocalSerializerFactory) = primTypes[type]!!(factory)
    }

    fun localWriteObject(data: Data, func: () -> Unit) {
        data.withDescribed(typeNotation.descriptor) { withList { func() } }
    }
}

class PrimIntArraySerializer(factory: LocalSerializerFactory) : PrimArraySerializer(IntArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as IntArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimCharArraySerializer(factory: LocalSerializerFactory) : PrimArraySerializer(CharArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as CharArray).forEach {
                output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1)
            }
        }
    }

    override fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType = type.asClass()
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            val array = this
            (0..lastIndex).forEach { java.lang.reflect.Array.set(array, it, (list[it] as Int).toChar()) }
        }
    }
}

class PrimBooleanArraySerializer(factory: LocalSerializerFactory) : PrimArraySerializer(BooleanArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as BooleanArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimDoubleArraySerializer(factory: LocalSerializerFactory) :
        PrimArraySerializer(DoubleArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as DoubleArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimFloatArraySerializer(factory: LocalSerializerFactory) :
        PrimArraySerializer(FloatArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int) {
        localWriteObject(data) {
            (obj as FloatArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimShortArraySerializer(factory: LocalSerializerFactory) :
        PrimArraySerializer(ShortArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as ShortArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimLongArraySerializer(factory: LocalSerializerFactory) :
        PrimArraySerializer(LongArray::class.java, factory) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as LongArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}
