package net.corda.layeredpropertymap.impl

import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.v5.base.types.MemberX500Name
import java.time.Instant
import java.util.UUID

/**
 * Converter class, converting from String to actual Objects.
 *
 * @property converters A map of converters which can be used as additional converters, besides the simpler
 * ones existing in this class.
 */
class PropertyConverter constructor(
    private val converters: Map<Class<out Any>, CustomPropertyConverter<out Any>>
) {
    @Suppress("UNCHECKED_CAST", "ComplexMethod")
    fun <T> convert(context: ConversionContext, clazz: Class<out T>): T? {
        val converter = converters[clazz]
        return if(converter != null) {
            converter.convert(context) as T
        } else {
            val value = context.value()
            return if (value == null) {
                null
            } else {
                when (clazz.kotlin) {
                    Int::class -> value.toInt() as T
                    Long::class -> value.toLong() as T
                    Short::class -> value.toShort() as T
                    Float::class -> value.toFloat() as T
                    Double::class -> value.toDouble() as T
                    Boolean::class -> value.toBoolean() as T
                    String::class -> value as T
                    Instant::class -> Instant.parse(value) as T
                    MemberX500Name::class -> MemberX500Name.parse(value) as T
                    UUID::class -> UUID.fromString(value) as T
                    else -> throw IllegalStateException("Unknown '${clazz.name}' type.")
                }
            }
        }
    }
}