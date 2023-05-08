package net.corda.flow.application.services.impl.interop.parameters

import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.util.*

/**
 * A [TypeParameters] is the type of a [TypedParameterImpl]. It is always one of a small set of primitive types, or
 * a [QualifiedType] qualifying a primitive type with a [TypeQualifier] which identifies a more complex type.
 */
class TypeParameters<T> {

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
     * @param typeString The string to parse.
     */
    fun <T : Any> of(typeString: String): ParameterType<T> = of(typeString, emptyMap())

    /**
     * Parse a [TypeParameters] from a string in the format "type" or "type (qualifier)".
     *
     * The accepted types are "boolean", "string", "decimal", "uuid", "timestamp", "bytes" and "json", or aliases
     * defined in the supplied [Map].
     * @param typeString The string to parse.
     * @param aliases A map of type aliases.
     */
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
        else QualifiedType(rawType, TypeQualifier.of(qualifierString))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> parseRawParameterType(typeName: String): ParameterType<T> {
        return when (typeName) {
            ParameterTypeLabel.BOOLEAN.typeName -> RawParameterType<Boolean>(ParameterTypeLabel.BOOLEAN)
            ParameterTypeLabel.STRING.typeName -> RawParameterType<String>(ParameterTypeLabel.STRING)
            ParameterTypeLabel.DECIMAL.typeName -> RawParameterType<BigDecimal>(ParameterTypeLabel.DECIMAL)
            ParameterTypeLabel.UUID.typeName -> RawParameterType<UUID>(ParameterTypeLabel.UUID)
            ParameterTypeLabel.TIMESTAMP.typeName -> RawParameterType<ZonedDateTime>(ParameterTypeLabel.TIMESTAMP)
            ParameterTypeLabel.BYTES.typeName -> RawParameterType<ByteBuffer>(ParameterTypeLabel.BYTES)
            ParameterTypeLabel.JSON.typeName -> RawParameterType<String>(ParameterTypeLabel.JSON)

            else -> throw IllegalArgumentException(
                "Invalid raw parameter type: $typeName - " +
                        "must be one of ${ParameterTypeLabel.values().map { it.typeName }}"
            )
        } as ParameterType<T>
    }
}
