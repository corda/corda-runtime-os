package net.corda.membership.impl.serialization

import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.impl.PartyImpl
import net.corda.membership.impl.parse
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.membership.identity.parser.CustomConversionContext
import net.corda.v5.membership.identity.parser.CustomObjectConverter
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [Party] object.
 */
@Component(service = [CustomObjectConverter::class])
class PartyConverter : CustomObjectConverter {
    companion object {
        private const val PARTY = "corda.party"
        private const val NOTARY_SERVICE_PARTY = "corda.notaryServiceParty"
    }

    override val type: Class<*>
        get() = Party::class.java

    override fun convert(context: CustomConversionContext): Party {
        return when(context.storeClass) {
            MemberContextImpl::class.java -> {
                when(context.key) {
                    PARTY ->  PartyImpl(
                        CordaX500Name(context.store.parse(PARTY_NAME)),
                        context.store.parse(PARTY_OWNING_KEY)
                    )
                    NOTARY_SERVICE_PARTY -> PartyImpl(
                        CordaX500Name.parse(context.store.parse(NOTARY_SERVICE_PARTY_NAME)),
                        context.store.parse(NOTARY_SERVICE_PARTY_KEY)
                    )
                    else -> throw IllegalArgumentException("Unknown key '${context.key}'.")
                }
            }
            else -> throw IllegalArgumentException("Unknown class '${context.store::class.java.name}'.")
        }
    }
}