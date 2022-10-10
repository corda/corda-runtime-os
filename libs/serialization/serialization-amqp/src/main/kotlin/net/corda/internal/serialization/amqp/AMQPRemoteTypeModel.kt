package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.NotSerializableDetailedException
import net.corda.internal.serialization.model.DefaultCacheProvider
import net.corda.internal.serialization.model.EnumTransforms
import net.corda.internal.serialization.model.InvalidEnumTransformsException
import net.corda.internal.serialization.model.RemotePropertyInformation
import net.corda.internal.serialization.model.RemoteTypeInformation
import net.corda.internal.serialization.model.TypeDescriptor
import net.corda.internal.serialization.model.TypeIdentifier
import java.io.NotSerializableException

/**
 * Interprets AMQP [Schema] information to obtain [RemoteTypeInformation], caching by [TypeDescriptor].
 */
class AMQPRemoteTypeModel {

    private val cache: MutableMap<TypeDescriptor, RemoteTypeInformation> = DefaultCacheProvider.createCache()

    /**
     * Interpret a [Schema] to obtain a [Map] of all of the [RemoteTypeInformation] contained therein, indexed by
     * [TypeDescriptor].
     *
     * A [Schema] contains a set of [TypeNotation]s, which we recursively convert into [RemoteTypeInformation],
     * associating each new piece of [RemoteTypeInformation] with the [TypeDescriptor] attached to it in the schema.
     *
     * We start by building a [Map] of [TypeNotation] by [TypeIdentifier], using [AMQPTypeIdentifierParser] to convert
     * AMQP type names into [TypeIdentifier]s. This is used as a lookup for resolving notations that are referred to by
     * type name from other notations, e.g. the types of properties.
     *
     * We also build a [Map] of [TypeNotation] by [TypeDescriptor], which we then convert into [RemoteTypeInformation]
     * while merging with the cache.
     */
    fun interpret(serializationSchemas: SerializationSchemas, classloadingContext: ClassloadingContext): Map<TypeDescriptor, RemoteTypeInformation> {
        val (schema, transforms) = serializationSchemas
        val notationLookup = schema.types.associateBy { it.name.getTypeIdentifier(classloadingContext) }
        val byTypeDescriptor = schema.types.associateBy { it.typeDescriptor }
        val enumTransformsLookup = transforms.types.asSequence().map { (name, transformSet) ->
            name.getTypeIdentifier(classloadingContext) to transformSet
        }.toMap()

        val interpretationState = InterpretationState(notationLookup, enumTransformsLookup, cache, emptySet())

        val result = byTypeDescriptor.mapValues { (typeDescriptor, typeNotation) ->
            cache.getOrPut(typeDescriptor) { interpretationState.run { typeNotation.name.getTypeIdentifier(classloadingContext).interpretIdentifier(classloadingContext) } }
        }
        val typesByIdentifier = result.values.associateBy { it.typeIdentifier }
        result.values.forEach { typeInformation ->
            if (typeInformation is RemoteTypeInformation.Cycle) {
                typeInformation.follow = typesByIdentifier[typeInformation.typeIdentifier] ?:
                        throw NotSerializableException("Cannot resolve cyclic reference to ${typeInformation.typeIdentifier}")
            }
        }
        return result
    }

    data class InterpretationState(val notationLookup: Map<TypeIdentifier, TypeNotation>,
                                   val enumTransformsLookup: Map<TypeIdentifier, TransformsMap>,
                                   val cache: MutableMap<TypeDescriptor, RemoteTypeInformation>,
                                   val seen: Set<TypeIdentifier>) {

        private inline fun <T> withSeen(typeIdentifier: TypeIdentifier, block: InterpretationState.() -> T): T =
                withSeen(seen + typeIdentifier, block)

        private inline fun <T> withSeen(seen: Set<TypeIdentifier>, block: InterpretationState.() -> T): T =
                copy(seen = seen).run(block)

        /**
         * Follow a [TypeIdentifier] to the [TypeNotation] associated with it in the lookup, and interpret that notation.
         * If there is no such notation, interpret the [TypeIdentifier] directly into [RemoteTypeInformation].
         *
         * If we have visited this [TypeIdentifier] before while traversing the graph of related [TypeNotation]s, then we
         * know we have hit a cycle and respond accordingly.
         */
        fun TypeIdentifier.interpretIdentifier(classloadingContext: ClassloadingContext): RemoteTypeInformation =
            if (this in seen) RemoteTypeInformation.Cycle(this)
            else withSeen(this) {
                val identifier = this@interpretIdentifier
                notationLookup[identifier]?.interpretNotation(identifier, classloadingContext) ?: interpretNoNotation(classloadingContext)
            }

        /**
         * Either fetch from the cache, or interpret, cache, and return, the [RemoteTypeInformation] corresponding to this
         * [TypeNotation].
         */
        private fun TypeNotation.interpretNotation(identifier: TypeIdentifier, classloadingContext: ClassloadingContext): RemoteTypeInformation =
                cache.getOrPut(typeDescriptor) {
                    when (this) {
                        is CompositeType -> interpretComposite(identifier, classloadingContext)
                        is RestrictedType -> interpretRestricted(identifier, classloadingContext)
                    }
                }

        /**
         * Interpret the properties, interfaces and type parameters in this [TypeNotation], and return suitable
         * [RemoteTypeInformation].
         */
        private fun CompositeType.interpretComposite(identifier: TypeIdentifier, classloadingContext: ClassloadingContext): RemoteTypeInformation {
            val properties = fields.asSequence().sortedBy { it.name }.map { it.interpret(classloadingContext) }.toMap(LinkedHashMap())
            val typeParameters = identifier.interpretTypeParameters(classloadingContext)
            val interfaceIdentifiers = provides.map { name -> name.getTypeIdentifier(classloadingContext) }
            val isInterface = identifier in interfaceIdentifiers
            val interfaces = interfaceIdentifiers.mapNotNull { interfaceIdentifier ->
                if (interfaceIdentifier == identifier) null
                else interfaceIdentifier.interpretIdentifier(classloadingContext)
            }

            return if (isInterface) RemoteTypeInformation.AnInterface(typeDescriptor, identifier, properties, interfaces, typeParameters)
            else RemoteTypeInformation.Composable(typeDescriptor, identifier, properties, interfaces, typeParameters)
        }

        /**
         * Type parameters are read off from the [TypeIdentifier] we translated the AMQP type name into.
         */
        private fun TypeIdentifier.interpretTypeParameters(classloadingContext: ClassloadingContext): List<RemoteTypeInformation> = when (this) {
            is TypeIdentifier.Parameterised -> parameters.map { it.interpretIdentifier(classloadingContext) }
            else -> emptyList()
        }

        /**
         * Interpret a [RestrictedType] into suitable [RemoteTypeInformation].
         */
        private fun RestrictedType.interpretRestricted(identifier: TypeIdentifier, classloadingContext: ClassloadingContext): RemoteTypeInformation = when (identifier) {
            is TypeIdentifier.Parameterised ->
                RemoteTypeInformation.Parameterised(
                        typeDescriptor,
                        identifier,
                        identifier.interpretTypeParameters(classloadingContext)
                )
            is TypeIdentifier.ArrayOf ->
                RemoteTypeInformation.AnArray(
                        typeDescriptor,
                        identifier,
                        identifier.componentType.interpretIdentifier(classloadingContext)
                )
            is TypeIdentifier.Unparameterised ->
                if (choices.isEmpty()) {
                    RemoteTypeInformation.Unparameterised(
                            typeDescriptor,
                            identifier)
                } else interpretEnum(identifier)

            else -> throw NotSerializableException("Cannot interpret restricted type $this")
        }

        private fun RestrictedType.interpretEnum(identifier: TypeIdentifier): RemoteTypeInformation.AnEnum {
            val constants = choices.asSequence().mapIndexed { index, choice -> choice.name to index }.toMap(LinkedHashMap())
            val transforms = try {
                enumTransformsLookup[identifier]?.let { EnumTransforms.build(it, constants) } ?: EnumTransforms.empty
            } catch (e: InvalidEnumTransformsException) {
                throw NotSerializableDetailedException(name, e.message!!)
            }
            return RemoteTypeInformation.AnEnum(
                    typeDescriptor,
                    identifier,
                    constants.keys.toList(),
                    transforms)
        }

        /**
         * Interpret a [Field] into a name/[RemotePropertyInformation] pair.
         */
        private fun Field.interpret(classloadingContext: ClassloadingContext): Pair<String, RemotePropertyInformation> {
            val identifier = type.getTypeIdentifier(classloadingContext)

            // A type of "*" is replaced with the value of the "requires" field
            val fieldTypeIdentifier = if (identifier == TypeIdentifier.TopType && !requires.isEmpty()) {
                requires[0].getTypeIdentifier(classloadingContext)
            } else identifier

            // We convert Java Object types to Java primitive types if the field is mandatory.
            val fieldType = fieldTypeIdentifier.forcePrimitive(mandatory).interpretIdentifier(classloadingContext)

            return name to RemotePropertyInformation(
                    fieldType,
                    mandatory)
        }

        /**
         * If there is no [TypeNotation] in the [Schema] matching a given [TypeIdentifier], we interpret the [TypeIdentifier]
         * directly.
         */
        private fun TypeIdentifier.interpretNoNotation(classloadingContext: ClassloadingContext): RemoteTypeInformation =
                when (this) {
                    is TypeIdentifier.TopType -> RemoteTypeInformation.Top
                    is TypeIdentifier.UnknownType -> RemoteTypeInformation.Unknown
                    is TypeIdentifier.ArrayOf ->
                        RemoteTypeInformation.AnArray(
                                name,
                                this,
                                componentType.interpretIdentifier(classloadingContext)
                        )
                    is TypeIdentifier.Parameterised ->
                        RemoteTypeInformation.Parameterised(
                                name,
                                this,
                                parameters.map { it.interpretIdentifier(classloadingContext) })
                    else -> RemoteTypeInformation.Unparameterised(name, this)
                }
    }
}

private val TypeNotation.typeDescriptor: String get() = descriptor.name?.toString() ?:
throw NotSerializableException("Type notation has no type descriptor: $this")

private fun String.getTypeIdentifier(classloadingContext: ClassloadingContext) = AMQPTypeIdentifierParser.parse(this, classloadingContext)

/**
 * Force e.g. [java.lang.Integer] to `int`, if it is the type of a mandatory field.
 */
private fun TypeIdentifier.forcePrimitive(mandatory: Boolean) =
        if (mandatory) primitives[this] ?: this
        else this

private val primitives = sequenceOf(
        Boolean::class,
        Byte::class,
        Char::class,
        Int::class,
        Short::class,
        Long::class,
        Float::class,
        Double::class).associate {
    TypeIdentifier.forClass(it.javaObjectType) to TypeIdentifier.forClass(it.javaPrimitiveType!!)
}