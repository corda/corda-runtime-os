package net.corda.membership.identity.converter

import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.v5.crypto.PublicKeyHash
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [PublicKeyHash] object.
 */
@Component(service = [CustomPropertyConverter::class])
class PublicKeyHashConverter : CustomPropertyConverter<PublicKeyHash> {
    override val type: Class<PublicKeyHash>
        get() = PublicKeyHash::class.java

    override fun convert(context: ConversionContext): PublicKeyHash? =
        context.value()?.let { PublicKeyHash.parse(it) }
}
