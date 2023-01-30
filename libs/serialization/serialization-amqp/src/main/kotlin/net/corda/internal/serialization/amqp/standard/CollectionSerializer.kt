package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.Descriptor
import net.corda.internal.serialization.amqp.resolveTypeVariables
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.withList
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ifThrowsAppend
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Collections
import java.util.NavigableSet
import java.util.SortedSet
import java.util.TreeSet

/**
 * Serialization / deserialization of predefined set of supported [Collection] types covering mostly [List]s and [Set]s.
 */
class CollectionSerializer(private val declaredType: ParameterizedType, factory: LocalSerializerFactory) :
    AMQPSerializer<Any> {
    override val type: Type = declaredType

    override val typeDescriptor: Symbol by lazy(LazyThreadSafetyMode.PUBLICATION) {
        factory.createDescriptor(type)
    }

    companion object {
        // NB: Order matters in this map, the most specific classes should be listed at the end
        private val supportedTypes: Map<Class<out Collection<*>>, (List<*>) -> Collection<*>> = Collections.unmodifiableMap(
            linkedMapOf(
                Collection::class.java to Collections::unmodifiableCollection,
                List::class.java to Collections::unmodifiableList,
                Set::class.java to { list -> Collections.unmodifiableSet(LinkedHashSet(list)) },
                SortedSet::class.java to { list -> Collections.unmodifiableSortedSet(TreeSet(list)) },
                NavigableSet::class.java to { list -> Collections.unmodifiableNavigableSet(TreeSet(list)) }
            )
        )

        private val supportedTypeIdentifiers = supportedTypes.keys.mapTo(LinkedHashSet(), TypeIdentifier::forClass)

        /**
         * Replace erased collection types with parameterised types with wildcard type parameters, so that they are represented
         * appropriately in the AMQP schema.
         */
        fun resolveDeclared(declaredTypeInformation: LocalTypeInformation.ACollection, sandboxGroup: SandboxGroup): LocalTypeInformation.ACollection {
            if (declaredTypeInformation.typeIdentifier.erased in supportedTypeIdentifiers)
                return reparameterise(declaredTypeInformation, sandboxGroup)

            throw NotSerializableException(
                "Cannot derive collection type for declared type: " +
                    declaredTypeInformation.prettyPrint(false)
            )
        }

        fun resolveActual(
            actualClass: Class<*>,
            declaredTypeInformation: LocalTypeInformation.ACollection,
            sandboxGroup: SandboxGroup
        ): LocalTypeInformation.ACollection {
            if (declaredTypeInformation.typeIdentifier.erased in supportedTypeIdentifiers)
                return reparameterise(declaredTypeInformation, sandboxGroup)

            val collectionClass = findMostSuitableCollectionType(actualClass)
            val erasedInformation = LocalTypeInformation.ACollection(
                collectionClass,
                TypeIdentifier.forClass(collectionClass),
                LocalTypeInformation.Unknown
            )

            return when (declaredTypeInformation.typeIdentifier) {
                is TypeIdentifier.Parameterised -> erasedInformation.withElementType(declaredTypeInformation.elementType, sandboxGroup)
                else -> erasedInformation.withElementType(LocalTypeInformation.Unknown, sandboxGroup)
            }
        }

        private fun reparameterise(typeInformation: LocalTypeInformation.ACollection, sandboxGroup: SandboxGroup): LocalTypeInformation.ACollection =
            when (typeInformation.typeIdentifier) {
                is TypeIdentifier.Parameterised -> typeInformation
                is TypeIdentifier.Erased -> typeInformation.withElementType(LocalTypeInformation.Unknown, sandboxGroup)
                else -> throw NotSerializableException(
                    "Unexpected type identifier ${typeInformation.typeIdentifier.prettyPrint(false)} " +
                        "for collection type ${typeInformation.prettyPrint(false)}"
                )
            }

        private fun findMostSuitableCollectionType(actualClass: Class<*>): Class<out Collection<*>> =
            supportedTypes.keys.findLast { it.isAssignableFrom(actualClass) }!!

        private fun findConcreteType(clazz: Class<*>): (List<*>) -> Collection<*> {
            return supportedTypes[clazz] ?: throw AMQPNotSerializableException(
                clazz,
                "Unsupported collection type $clazz.",
                "Supported Collections are ${supportedTypes.keys.joinToString(",")}"
            )
        }
    }

    private val concreteBuilder: (List<*>) -> Collection<*> = findConcreteType(declaredType.rawType as Class<*>)

    private val typeNotation: TypeNotation = RestrictedType(AMQPTypeIdentifiers.nameForType(declaredType), null, emptyList(), "list", Descriptor(typeDescriptor), emptyList())

    private val outboundType = resolveTypeVariables(declaredType.actualTypeArguments[0], null, factory.sandboxGroup)
    private val inboundType = declaredType.actualTypeArguments[0]

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) = ifThrowsAppend(declaredType::getTypeName) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(outboundType, context)
        }
    }

    override fun writeObject(
        obj: Any,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext,
        debugIndent: Int
    ) = ifThrowsAppend(declaredType::getTypeName) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            withList {
                for (entry in obj as Collection<*>) {
                    output.writeObjectOrNull(entry, this, outboundType, context, debugIndent)
                }
            }
        }
    }

    override fun readObject(
        obj: Any,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        input: DeserializationInput,
        context: SerializationContext
    ): Any = ifThrowsAppend(declaredType::getTypeName) {
        // TODO: Can we verify the entries in the list?
        concreteBuilder(
            (obj as List<*>).map {
                input.readObjectOrNull(it, serializationSchemas, metadata, inboundType, context)
            }
        )
    }
}
