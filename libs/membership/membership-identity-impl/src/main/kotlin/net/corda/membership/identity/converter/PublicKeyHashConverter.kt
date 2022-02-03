package net.corda.membership.identity.converter

import net.corda.membership.identity.MemberContextImpl
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.conversion.ConversionContext
import net.corda.v5.membership.conversion.CustomPropertyConverter
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [PublicKeyHash] object.
 */
@Component(service = [CustomPropertyConverter::class])
class PublicKeyHashConverter : CustomPropertyConverter<PublicKeyHash> {
    override val type: Class<PublicKeyHash>
        get() = PublicKeyHash::class.java

    /**
     * We try to select the first element in case the structure is like 'corda.identityKeyHashes.1'.
     */
    override fun convert(context: ConversionContext): PublicKeyHash? {
         return when(context.storeClass) {
            MemberContextImpl::class.java -> {
                val hashString = context.store.entries.firstOrNull()?.value
                hashString?.let { PublicKeyHash.parse(hashString) }
            }
            else -> throw IllegalArgumentException("Unknown class '${context.store::class.java.name}'.")
        }

    }
}
