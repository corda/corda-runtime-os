package net.corda.membership.impl

import net.corda.v5.application.identity.AbstractParty
import net.corda.v5.application.identity.AnonymousParty
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.PartyAndReference
import net.corda.v5.base.types.OpaqueBytes
import java.security.PublicKey

class AnonymousPartyImpl(override val owningKey: PublicKey) : AnonymousParty {
    override fun nameOrNull(): CordaX500Name? = null
    override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)
    override fun toString() = "Anonymous(${owningKey})"

    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other === this || other is AbstractParty && other.owningKey == owningKey

    override fun hashCode(): Int = owningKey.hashCode()
}