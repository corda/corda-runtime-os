package net.corda.layeredpropertymap

/**
 * Converter interface for more complex types (such as Party, EndpointInfo, etc).
 * The converters must be implemented as OSGi components in order for the [LayeredPropertyMapFactory] to pick them up.
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