package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier
import java.util.*

/**
 * A [TypeParameters] is the type of a [TypedParameter]. It is always one of a small set of primitive types, or
 * a [QualifiedType] qualifying a primitive type with a [FacadeTypeQualifier] which identifies a more complex type.
 */
final class TypeParameters<T> {

    companion object {

        /**
         * This pattern matches (after a whitespace prefix of any length) either a single non-whitespace string, e.g.
         * "uuid", or a pair of non-whitespace strings separated by at least one character of whitespace, with the
         * second string surrounded by parentheses.
         *
         * For example: "denomination  (org.corda.interop/finance/tokens/denomination/v1.0)".
         */
        private val facadeTypeRegex = Regex("""\s*(\S+)(\s+\((\S+)\))?.*""")

        /**
         * Parse a [TypeParameters] from a string in the format "type" or "type (qualifier)".
         *
         * The accepted types are "boolean", "string", "decimal", "uuid", "timestamp", "bytes" and "json".
         *
         * @param typeString The string to parse.
         */
        @JvmStatic
        fun <T : Any> of(typeString: String): ParameterType<T> = of(typeString, emptyMap())

        /**
         * Parse a [TypeParameters] from a string in the format "type" or "type (qualifier)".
         *
         * The accepted types are "boolean", "string", "decimal", "uuid", "timestamp", "bytes" and "json", or aliases
         * defined in the supplied [Map].
         *
         * @param typeString The string to parse.
         * @param aliases A map of type aliases.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> of(typeString: String, aliases: Map<String, TypeParameters<*>>): ParameterType<T> {
            val typeMatch = facadeTypeRegex.matchEntire(typeString)
                ?: throw IllegalArgumentException("Invalid parameter type: $typeString")

            val rawTypeName = typeMatch.groups[1]!!.value
            val qualifierString = typeMatch.groups[3]?.value
            val aliased = aliases[rawTypeName]
            if (aliased != null) {
                if (qualifierString != null) {
                    throw IllegalArgumentException("Alias $rawTypeName cannot be qualified with $qualifierString")
                }
                return aliased as ParameterType<T>
            }

            val rawType = parseRawParameterType(rawTypeName)
            return if (qualifierString == null) rawType
            else QualifiedType(rawType, TypeQualifier.of(qualifierString))
        }

        private fun parseRawParameterType(typeName: String): ParameterType<*>? {
            return ParameterTypeLabel.parse(typeName).expectedClass
        }

//        /**
//         * The expected type of a [TypedParameterValue] for this [ParameterTypeImpl].
//         */
//        @Suppress("UNCHECKED_CAST")
//        open val expectedType: Class<T>
//            get() {
//                val superclass = this::class.java.genericSuperclass as ParameterizedType
//                return superclass.actualTypeArguments[0] as Class<T>
//            }
    }
}
