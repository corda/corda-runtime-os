package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.model.resolveAgainst
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.loggerFor
import net.corda.v5.base.util.trace
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Serialization / deserialization of arrays.
 */
open class ArraySerializer(override val type: Type, factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) : AMQPSerializer<Any> {
    companion object {
        fun make(type: Type, factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) : AMQPSerializer<Any> {
            contextLogger().debug { "Making array serializer, typename=${type.typeName}" }
            return when (type) {
                Array<Char>::class.java -> CharArraySerializer(factory,sandboxGroup)
                else -> ArraySerializer(type, factory, sandboxGroup)
            }
        }
    }

    private val logger = loggerFor<ArraySerializer>()

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

    override val typeDescriptor: Symbol by lazy {
        factory.createDescriptor(type, sandboxGroup)
    }

    internal val elementType: Type by lazy { type.componentType().resolveAgainst(type) }
    internal open val typeName by lazy { calcTypeName(type) }

    internal val typeNotation: TypeNotation by lazy {
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
class CharArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) : ArraySerializer(Array<Char>::class.java, factory, sandboxGroup) {
    override fun <T> List<T>.toArrayOfType(type: Type): Any {
        val elementType = type.asClass()
        val list = this
        return java.lang.reflect.Array.newInstance(elementType, this.size).apply {
            (0..lastIndex).forEach { java.lang.reflect.Array.set(this, it, (list[it] as Int).toChar()) }
        }
    }
}

// Specialisation of [ArraySerializer] that handles arrays of unboxed java primitive types
abstract class PrimArraySerializer(type: Type, factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) : ArraySerializer(type, factory, sandboxGroup) {
    companion object {

        fun make(type: Type, factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) =
            mapOf<Type, (LocalSerializerFactory) -> PrimArraySerializer>(
                    IntArray::class.java to { f -> PrimIntArraySerializer(f, sandboxGroup) },
                    CharArray::class.java to { f -> PrimCharArraySerializer(f, sandboxGroup) },
                    BooleanArray::class.java to { f -> PrimBooleanArraySerializer(f, sandboxGroup) },
                    FloatArray::class.java to { f -> PrimFloatArraySerializer(f, sandboxGroup) },
                    ShortArray::class.java to { f -> PrimShortArraySerializer(f, sandboxGroup) },
                    DoubleArray::class.java to { f -> PrimDoubleArraySerializer(f, sandboxGroup) },
                    LongArray::class.java to { f -> PrimLongArraySerializer(f, sandboxGroup) }
                    // ByteArray::class.java <-> NOT NEEDED HERE (see comment above)
            )[type]!!(factory)
    }

    fun localWriteObject(data: Data, func: () -> Unit) {
        data.withDescribed(typeNotation.descriptor) { withList { func() } }
    }
}

class PrimIntArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) : PrimArraySerializer(IntArray::class.java, factory, sandboxGroup) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as IntArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimCharArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) : PrimArraySerializer(CharArray::class.java, factory, sandboxGroup) {
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

class PrimBooleanArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) : PrimArraySerializer(
    BooleanArray::class.java,
    factory,
    sandboxGroup) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as BooleanArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimDoubleArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) :
        PrimArraySerializer(DoubleArray::class.java, factory, sandboxGroup) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as DoubleArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimFloatArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) :
        PrimArraySerializer(FloatArray::class.java, factory, sandboxGroup) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int) {
        localWriteObject(data) {
            (obj as FloatArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimShortArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) :
        PrimArraySerializer(ShortArray::class.java, factory, sandboxGroup) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as ShortArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}

class PrimLongArraySerializer(factory: LocalSerializerFactory, sandboxGroup: SandboxGroup) :
        PrimArraySerializer(LongArray::class.java, factory, sandboxGroup) {
    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        localWriteObject(data) {
            (obj as LongArray).forEach { output.writeObjectOrNull(it, data, elementType, context, debugIndent + 1) }
        }
    }
}
