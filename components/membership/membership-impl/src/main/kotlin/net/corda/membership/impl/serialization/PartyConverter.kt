package net.corda.membership.impl.serialization

import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.PartyImpl
import net.corda.membership.impl.parse
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.membership.conversion.ConversionContext
import net.corda.v5.membership.conversion.CustomPropertyConverter
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [Party] object.
 */
@Component(service = [CustomPropertyConverter::class])
class PartyConverter : CustomPropertyConverter<Party> {
    companion object {
        private const val NAME = "name"
        private const val OWNING_KEY = "owningKey"
    }

    override val type: Class<Party>
        get() = Party::class.java

    override fun convert(context: ConversionContext): Party {
        return when(context.storeClass) {
            MemberContextImpl::class.java -> {
                PartyImpl(
                    CordaX500Name(context.store.parse(context.key + "." + NAME)),
                    context.store.parse(context.key + "." + OWNING_KEY)
                )
            }
            else -> throw IllegalArgumentException("Unknown class '${context.store::class.java.name}'.")
        }
    }
}