package net.corda.v5.ledger.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.toStringShort
import java.security.PublicKey

/**
 * Defines a well-known identity.
 *
 * @property name The well-known [MemberX500Name] that represents the current identity.
 * @property owningKey The [PublicKey] that represents the current identity.
 */
@CordaSerializable
data class Party(val name: MemberX500Name, val owningKey: PublicKey) {

    /**
     * Gets the [String] representation of the current object.
     *
     * @return Returns the [String] representation of the current object.
     */
    override fun toString(): String {
        return "$name (owningKey = ${owningKey.toStringShort()})"
    }
}
