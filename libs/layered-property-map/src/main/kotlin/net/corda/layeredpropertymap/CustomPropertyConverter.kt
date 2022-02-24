package net.corda.layeredpropertymap

/**
 * Converter interface for more complex types (such as Party, EndpointInfo, etc).
 */
interface CustomPropertyConverter<T> {
    /**
     * Type of the class the converter is for.
     */
    val type: Class<T>

    /**
     * Converts value referenced by the [context].
     */
    fun convert(context: ConversionContext): T?
}