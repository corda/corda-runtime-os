package net.corda.ledger.consensual.impl

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.toStringShort
import net.corda.v5.ledger.consensual.Party
import java.security.PublicKey

/**
 * CORE-5936
 */
class PartyImpl(override val name: MemberX500Name, override val owningKey: PublicKey) : Party {
    override fun toString() = "$name (owningKey = ${owningKey.toStringShort()})"
    override fun equals(other: Any?): Boolean =
        other === this ||
                other is Party &&
                other.owningKey == owningKey &&
                other.name == name
    override fun hashCode(): Int = owningKey.hashCode()
}