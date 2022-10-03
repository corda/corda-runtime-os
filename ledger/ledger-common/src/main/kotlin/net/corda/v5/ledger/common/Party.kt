package net.corda.v5.ledger.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

/**
 * Defines a well-known identity.
 *
 * @property name The well-known [MemberX500Name] that represents the current identity.
 * @property owningKey The [PublicKey] that represents the current identity.
 */
@CordaSerializable
interface Party {
    val name: MemberX500Name
    val owningKey: PublicKey
}
