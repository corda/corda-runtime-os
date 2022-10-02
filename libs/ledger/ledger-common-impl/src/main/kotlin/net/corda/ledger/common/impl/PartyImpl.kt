package net.corda.ledger.common.impl

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.toStringShort
import net.corda.v5.ledger.common.Party
import java.security.PublicKey

/**
 * Represents a well-known identity.
 *
 * @property name The well-known [MemberX500Name] that represents the current identity.
 * @property owningKey The [PublicKey] that represents the current identity.
 */
data class PartyImpl(override val name: MemberX500Name, override val owningKey: PublicKey) : Party {

    /**
     * Gets the [String] representation of the current object.
     *
     * @return Returns the [String] representation of the current object.
     */
    override fun toString(): String {
        return "$name (owningKey = ${owningKey.toStringShort()})"
    }
}