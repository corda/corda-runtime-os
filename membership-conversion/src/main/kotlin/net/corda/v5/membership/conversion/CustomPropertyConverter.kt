package net.corda.v5.membership.conversion

/**
 * Converter interface for more complex types (such as Party, EndpointInfo, etc).
 */
interface CustomPropertyConverter<T> {
    /**
     * Type of the class the converter is for.
     */
    val type: Class<T>
    fun convert(context: ConversionContext): T?
}