package net.corda.membership.impl.serialization

import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.conversion.ConversionContext
import net.corda.v5.membership.conversion.CustomPropertyConverter
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

/**
 * Converter class, converting from String to [PublicKey] object.
 *
 * @property keyEncodingService to convert the strings into PublicKeys
 */
@Component(service = [CustomPropertyConverter::class])
class PublicKeyConverter(private val keyEncodingService: KeyEncodingService): CustomPropertyConverter<PublicKey> {
    override val type: Class<PublicKey>
        get() = PublicKey::class.java

    /**
     * We either try to select the single element in case the structure is like 'corda.identityKeys.1'
     * or fall back to PARTY_OWNING_KEY if it has more than 1 element.
     */
    override fun convert(context: ConversionContext): PublicKey? {
        return when(context.storeClass) {
            MemberContextImpl::class.java -> {
                val keyOrOwningKey = context.store.entries.singleOrNull()?.value ?: context.store[PARTY_OWNING_KEY]
                keyOrOwningKey?.let { keyEncodingService.decodePublicKey(it) }
            }
            else -> throw IllegalArgumentException("Unknown class '${context.store::class.java.name}'")
        }

    }
}