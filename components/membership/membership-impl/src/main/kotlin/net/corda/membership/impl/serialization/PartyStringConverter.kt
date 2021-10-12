package net.corda.membership.impl.serialization

import net.corda.membership.impl.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.impl.PartyImpl
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.membership.identity.KeyValueStore
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.v5.membership.identity.StringObjectConverter
import net.corda.v5.membership.identity.ValueNotFoundException
import java.security.PublicKey

class PartyStringConverter : StringObjectConverter<Party> {
    override fun convert(stringProperties: KeyValueStore, clazz: Class<out Party>): Party {
        val (partyName, owningKey) = try {
            Pair(
                stringProperties.parse(PARTY_NAME, MemberX500Name::class.java),
                stringProperties.parse(PARTY_OWNING_KEY, PublicKey::class.java)
            )
        } catch (e: ValueNotFoundException) {

            // TODO log msg
            Pair(
                stringProperties.parse(NOTARY_SERVICE_PARTY_NAME, MemberX500Name::class.java),
                stringProperties.parse(NOTARY_SERVICE_PARTY_KEY, PublicKey::class.java)
            )
        }

        return PartyImpl(CordaX500Name(partyName), owningKey)
    }
}