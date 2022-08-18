package net.corda.v5.ledger.consensual

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

/**
 * Representation of a Party.
 *
 *
 * Since used in [ConsensualState] interface, their implementors will depend on it.
 * To make those implementations serializable, we use a custom serializer in the background, so
 * the normal @CordaSerializable cannot be used here.
 */
@DoNotImplement
interface Party {
    /**
     * @property name X500 Name of the party.
     */
    val name: MemberX500Name

    /**
     * @property owningKey The [PublicKey] owned by the party.
     */
    val owningKey: PublicKey
}