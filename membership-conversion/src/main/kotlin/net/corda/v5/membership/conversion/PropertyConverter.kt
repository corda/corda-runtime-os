package net.corda.v5.membership.conversion

/**
 * Converter class, converting from String to actual Objects.
 */
interface PropertyConverter {
    /**
     * Convert function, which does the parsing from String.
     *
     * @param context The context in which we can find the value.
     * @param clazz The class we want to convert to.
     */
    fun <T> convert(context: ConversionContext, clazz: Class<out T>): T?
}