package net.corda.membership.ledger.identity

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.ledger.identity.AbstractParty
import net.corda.v5.ledger.identity.AnonymousParty
import net.corda.v5.ledger.identity.PartyAndReference
import java.security.PublicKey

class AnonymousPartyImpl(override val owningKey: PublicKey) : AnonymousParty {
    override fun nameOrNull(): MemberX500Name? = null
    override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)
    override fun toString() = "Anonymous(${owningKey})"

    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other === this || other is AbstractParty && other.owningKey == owningKey

    override fun hashCode(): Int = owningKey.hashCode()
}