package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.internal.serialization.amqp.asClass
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.Descriptor
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.resolveTypeVariables
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.ifThrowsAppend
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.serialization.SerializationContext
import net.corda.sandbox.SandboxGroup
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Collections
import java.util.Dictionary
import java.util.EnumMap
import java.util.NavigableMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.WeakHashMap

private typealias MapCreationFunction = (Map<*, *>) -> Map<*, *>

/**
 * Serialization / deserialization of certain supported [Map] types.
 */
class MapSerializer(private val declaredType: ParameterizedType, factory: LocalSerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType

    override val typeDescriptor: Symbol = factory.createDescriptor(type)

    companion object {
        // NB: Order matters in this map, the most specific classes should be listed at the end
        private val supportedTypes: Map<Class<out Map<*, *>>, MapCreationFunction> = Collections.unmodifiableMap(linkedMapOf(
                // Interfaces
                Map::class.java to { map -> Collections.unmodifiableMap(map) },
                SortedMap::class.java to { map -> Collections.unmodifiableSortedMap(TreeMap(map)) },
                NavigableMap::class.java to { map -> Collections.unmodifiableNavigableMap(TreeMap(map)) },
                // concrete classes for user convenience
                LinkedHashMap::class.java to { map -> LinkedHashMap(map) },
                TreeMap::class.java to { map -> TreeMap(map) },
                EnumMap::class.java to { map ->
                    @Suppress("unchecked_cast")
                    EnumMap(map as Map<EnumJustUsedForCasting, Any>)
                }
        ))

        private val supportedTypeIdentifiers = supportedTypes.keys.asSequence()
                .map { TypeIdentifier.forGenericType(it) }.toSet()

        private fun findConcreteType(clazz: Class<*>): MapCreationFunction {
            return supportedTypes[clazz] ?: throw AMQPNotSerializableException(clazz, "Unsupported map type $clazz.")
        }

        fun resolveDeclared(declaredTypeInformation: LocalTypeInformation.AMap, sandboxGroup: SandboxGroup): LocalTypeInformation.AMap {
            declaredTypeInformation.observedType.asClass().checkSupportedMapType()
            if (supportedTypeIdentifiers.contains(declaredTypeInformation.typeIdentifier.erased))
                return if (!declaredTypeInformation.isErased) declaredTypeInformation
                else declaredTypeInformation.withParameters(LocalTypeInformation.Unknown, LocalTypeInformation.Unknown, sandboxGroup)

            throw NotSerializableException("Cannot derive map type for declared type " +
                    declaredTypeInformation.prettyPrint(false))
        }

        fun resolveActual(actualClass: Class<*>, declaredTypeInformation: LocalTypeInformation.AMap, sandboxGroup: SandboxGroup): LocalTypeInformation.AMap {
            declaredTypeInformation.observedType.asClass().checkSupportedMapType()
            if (supportedTypeIdentifiers.contains(declaredTypeInformation.typeIdentifier.erased)) {
                return if (!declaredTypeInformation.isErased) declaredTypeInformation
                else declaredTypeInformation.withParameters(LocalTypeInformation.Unknown, LocalTypeInformation.Unknown, sandboxGroup)
            }

            val mapClass = findMostSuitableMapType(actualClass)
            val erasedInformation = LocalTypeInformation.AMap(
                    mapClass,
                    TypeIdentifier.forClass(mapClass),
                    LocalTypeInformation.Unknown, LocalTypeInformation.Unknown)

            return when(declaredTypeInformation.typeIdentifier) {
                is TypeIdentifier.Parameterised -> erasedInformation.withParameters(
                        declaredTypeInformation.keyType,
                        declaredTypeInformation.valueType,
                        sandboxGroup)
                else -> erasedInformation.withParameters(LocalTypeInformation.Unknown, LocalTypeInformation.Unknown, sandboxGroup)
            }
        }

        private fun findMostSuitableMapType(actualClass: Class<*>): Class<out Map<*, *>> =
                supportedTypes.keys.findLast { it.isAssignableFrom(actualClass) }!!
    }

    private val concreteBuilder: MapCreationFunction = findConcreteType(declaredType.rawType as Class<*>)

    private val typeNotation: TypeNotation = RestrictedType(
        AMQPTypeIdentifiers.nameForType(declaredType),
        null,
        emptyList(),
        "map",
        Descriptor(typeDescriptor),
        emptyList()
    )

    private val inboundKeyType = declaredType.actualTypeArguments[0]
    private val outboundKeyType = resolveTypeVariables(inboundKeyType, null, factory.sandboxGroup)
    private val inboundValueType = declaredType.actualTypeArguments[1]
    private val outboundValueType = resolveTypeVariables(inboundValueType, null, factory.sandboxGroup)

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) = ifThrowsAppend({ declaredType.typeName }) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(outboundKeyType, context)
            output.requireSerializer(outboundValueType, context)
        }
    }

    override fun writeObject(
            obj: Any,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int
    ) = ifThrowsAppend({ declaredType.typeName }) {
        obj.javaClass.checkSupportedMapType()
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            // Write map
            data.putMap()
            data.enter()
            for ((key, value) in obj as Map<*, *>) {
                output.writeObjectOrNull(key, data, outboundKeyType, context, debugIndent)
                output.writeObjectOrNull(value, data, outboundValueType, context, debugIndent)
            }
            data.exit() // exit map
        }
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext
    ): Any = ifThrowsAppend({ declaredType.typeName }) {
        val entries: Iterable<Pair<Any?, Any?>> = (obj as Map<*, *>).map { readEntry(serializationSchemas, metadata, input, it, context) }
        concreteBuilder(entries.toMap())
    }

    private fun readEntry(serializationSchemas: SerializationSchemas, metadata: Metadata, input: DeserializationInput,
                          entry: Map.Entry<Any?, Any?>, context: SerializationContext
    ) = input.readObjectOrNull(entry.key, serializationSchemas, metadata, inboundKeyType, context) to
            input.readObjectOrNull(entry.value, serializationSchemas, metadata, inboundValueType, context)

    // Cannot use * as a bound for EnumMap and EnumSet since * is not an enum.  So, we use a sample enum instead.
    // We don't actually care about the type, we just need to make the compiler happier.
    internal enum class EnumJustUsedForCasting { NOT_USED }
}

internal fun Class<*>.checkSupportedMapType() {
    checkHashMap()
    checkWeakHashMap()
    checkDictionary()
}

private fun Class<*>.checkHashMap() {
    if (HashMap::class.java.isAssignableFrom(this) && !LinkedHashMap::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException(
                "Map type $this is unstable under iteration. Suggested fix: use java.util.LinkedHashMap instead.")
    }
}

/**
 * The [WeakHashMap] class does not exist within the DJVM, and so we need
 * to isolate this reference.
 */
private fun Class<*>.checkWeakHashMap() {
    if (WeakHashMap::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException("Weak references with map types not supported. Suggested fix: "
                + "use java.util.LinkedHashMap instead.")
    }
}

private fun Class<*>.checkDictionary() {
    if (Dictionary::class.java.isAssignableFrom(this)) {
        throw IllegalArgumentException(
                "Unable to serialise deprecated type $this. Suggested fix: prefer java.util.map implementations")
    }
}

