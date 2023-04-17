package net.corda.crypto.impl.converter

import net.corda.crypto.core.SecureHashImpl
import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [PublicKeyHash] object.
 */
@Component(service = [CustomPropertyConverter::class])
class PublicKeyHashConverter : CustomPropertyConverter<SecureHash> {
    override val type: Class<SecureHash>
        get() = SecureHash::class.java

    override fun convert(context: ConversionContext): SecureHash? = if (context.value("hash") != null) {
        context.value("hash")?.let { SecureHashImpl(DigestAlgorithmName.SHA2_256.name, it.toByteArray()) }
    } else {
        context.value()?.let { SecureHashImpl(DigestAlgorithmName.SHA2_256.name, it.toByteArray()) }
    }
}
