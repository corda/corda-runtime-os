package net.corda.crypto.impl.converter

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [PublicKeyHash] object.
 */
@Component(service = [CustomPropertyConverter::class])
class PublicKeyHashConverter : CustomPropertyConverter<PublicKeyHash> {
    override val type: Class<PublicKeyHash>
        get() = PublicKeyHash::class.java

    override fun convert(context: ConversionContext): PublicKeyHash? = if (context.value("hash") != null) {
        context.value("hash")?.let { PublicKeyHash.parse(it) }
    } else {
        context.value()?.let { PublicKeyHash.parse(it) }
    }
}
