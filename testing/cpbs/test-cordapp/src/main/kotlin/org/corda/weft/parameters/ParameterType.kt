package org.corda.weft.parameters

import org.corda.weft.api.HierarchicalName
import org.corda.weft.parameters.ParameterType.QualifiedType
import java.lang.reflect.ParameterizedType
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.util.*

/**
 * A [ParameterType] is the type of a [TypedParameter]. It is always one of a small set of primitive types, or
 * a [QualifiedType] qualifying a primitive type with a [FacadeTypeQualifier] which identifies a more complex type.
 */
sealed class ParameterType<T> {

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
         * Parse a [ParameterType] from a string in the format "type" or "type (qualifier)".
         *
         * The accepted types are "boolean", "string", "decimal", "uuid", "timestamp", "bytes" and "json".
         *
         * @param typeString The string to parse.
         */
        @JvmStatic
        fun <T : Any> of(typeString: String): ParameterType<T> = of(typeString, emptyMap())

        /**
         * Parse a [ParameterType] from a string in the format "type" or "type (qualifier)".
         *
         * The accepted types are "boolean", "string", "decimal", "uuid", "timestamp", "bytes" and "json", or aliases
         * defined in the supplied [Map].
         *
         * @param typeString The string to parse.
         * @param aliases A map of type aliases.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> of(typeString: String, aliases: Map<String, ParameterType<*>>): ParameterType<T> {
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

            val rawType = parseRawParameterType<T>(rawTypeName)
            return if (qualifierString == null) rawType
            else QualifiedType(rawType, FacadeTypeQualifier.of(qualifierString))
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> parseRawParameterType(typeName: String): ParameterType<T> =
            when (typeName) {
                "boolean" -> BooleanType
                "string" -> StringType
                "decimal" -> DecimalType
                "uuid" -> UUIDType
                "timestamp" -> TimestampType
                "bytes" -> ByteBufferType
                "json" -> JsonType

                else -> throw IllegalArgumentException(
                    "Invalid raw parameter type: $typeName - " +
                            "must be one of boolean, string, decimal, uuid, timestamp, bytes or json"
                )
            } as ParameterType<T>
    }

    /**
     * The expected type of a [TypedParameterValue] for this [ParameterType].
     */
    @Suppress("UNCHECKED_CAST")
    open val expectedType: Class<T>
        get() {
            val superclass = this::class.java.genericSuperclass as ParameterizedType
            return superclass.actualTypeArguments[0] as Class<T>
        }

    object BooleanType : ParameterType<Boolean>() {
        override fun toString() = "boolean"
    }

    object StringType : ParameterType<String>() {
        override fun toString() = "string"
    }

    object DecimalType : ParameterType<BigDecimal>() {
        override fun toString() = "decimal"
    }

    object UUIDType : ParameterType<UUID>() {
        override fun toString() = "uuid"
    }

    object TimestampType : ParameterType<ZonedDateTime>() {
        override fun toString() = "timestamp"
    }

    object ByteBufferType : ParameterType<ByteBuffer>() {
        override fun toString() = "bytes"
    }

    object JsonType : ParameterType<String>() {
        override fun toString() = "json"
    }

    data class QualifiedType<T>(
        val type: ParameterType<T>,
        val qualifier: FacadeTypeQualifier
    ) : ParameterType<T>() {
        override val expectedType: Class<T> get() = type.expectedType
        override fun toString() = "$type ($qualifier)"
    }
}

/**
 * A [FacadeTypeQualifier] qualifies a [ParameterType] with a versioned identity, which may be linked to a schema
 * or validation rules for that type.
 *
 * @param owner The owner of the type, e.g. "org.corda".
 * @param name The name of the type, e.g. "platform/tokens/Amount".
 * @param version The version of the type, e.g. "1.0".
 */
data class FacadeTypeQualifier(val owner: String, val name: HierarchicalName, val version: String) {

    companion object {

        /**
         * Construct a [FacadeTypeQualifier] from a string of the form "org.owner/hierarchical/name/version".
         *
         * @param qualifierString The string to build a [FacadeTypeQualifier] from.
         */
        fun of(qualifierString: String): FacadeTypeQualifier {
            val parts = qualifierString.split("/")
            if (parts.size < 3) {
                throw IllegalArgumentException("Invalid Facade Type Qualifier: $qualifierString")
            }
            return FacadeTypeQualifier(parts[0], parts.subList(1, parts.size - 1), parts.last())
        }
    }

    override fun toString() = "$owner/${name.joinToString("/")}/$version"
}