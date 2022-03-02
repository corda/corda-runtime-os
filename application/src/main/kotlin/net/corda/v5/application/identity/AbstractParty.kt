package net.corda.v5.application.identity

import net.corda.v5.application.flows.Destination
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.types.OpaqueBytes
import java.security.PublicKey

/**
 * An [AbstractParty] contains the common elements of [Party] and [AnonymousParty], specifically the owning key of
 * the party. In most cases [Party] or [AnonymousParty] should be used, depending on use-case.
 */
@CordaSerializable
@DoNotImplement
interface AbstractParty : Destination {
    val owningKey: PublicKey

    fun nameOrNull(): MemberX500Name?

    /**
     * Build a reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
     * ledger.
     */
    fun ref(bytes: OpaqueBytes): PartyAndReference

    /**
     * Build a reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
     * ledger.
     */
    @Suppress("SpreadOperator")
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}