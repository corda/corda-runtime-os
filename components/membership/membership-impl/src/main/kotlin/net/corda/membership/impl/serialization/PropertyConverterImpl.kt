package net.corda.membership.impl.serialization

import net.corda.v5.membership.converter.ConversionContext
import net.corda.v5.membership.converter.CustomPropertyConverter
import net.corda.v5.membership.converter.PropertyConverter
import net.corda.v5.membership.identity.MemberX500Name
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import java.time.Instant

/**
 * Converter class, converting from String to actual Objects.
 *
 * @property customConverters A list of converters which can be used as additional converters, besides the simpler
 * ones existing in this class.
 */
@Component(service = [PropertyConverter::class])
open class PropertyConverterImpl(
    @Reference(
        service = CustomPropertyConverter::class,
        cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY
    )
    val customConverters: List<CustomPropertyConverter<out Any>>
) : PropertyConverter {
    private val converters = customConverters.associateBy { it.type }.toMutableMap()

    @Suppress("UNCHECKED_CAST", "ComplexMethod")
    override fun <T> convert(context: ConversionContext, clazz: Class<out T>): T? {
        val converter = converters[clazz]
        return if(converter != null) {
            converter.convert(
                ConversionContext(
                    context.store,
                    context.storeClass,
                    context.key
                )
            ) as T
        } else {
            val value = context.value
            return if (value == null) {
                null
            } else {
                when (clazz.kotlin) {
                    Int::class -> value.toInt() as T
                    Long::class -> value.toLong() as T
                    Short::class -> value.toShort() as T
                    Float::class -> value.toFloat() as T
                    Double::class -> value.toDouble() as T
                    String::class -> value as T
                    Instant::class -> Instant.parse(value) as T
                    MemberX500Name::class -> MemberX500Name.parse(value) as T
                    else -> throw IllegalStateException("Unknown '${clazz.name}' type.")
                }
            }
        }
    }
}