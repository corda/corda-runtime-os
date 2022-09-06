package net.corda.v5.membership

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

/**
 * The member information consist of two parts:
 * Member provided context: Parameters added and signed by member as part of the initial MemberInfo proposal.
 * MGM provided context: Parameters added by MGM as a part of member acceptance.
 * Internally visible properties are accessible via extension properties.
 */
@CordaSerializable
interface MemberInfo {
    /**
     * Context representing the member set data regarding this members information.
     * Required data from this context is parsed and returned via other class properties or extension properties
     * internally.
     */
    val memberProvidedContext: MemberContext

    /**
     * Context representing the MGM set data regarding this members information.
     * Required data from this context is parsed and returned via other class properties or extension properties
     * internally.
     */
    val mgmProvidedContext: MGMContext

    /**
     * Member's X.500 name.
     * x.500 name is unique within the group and cannot be changed while the membership exists.
     */
    val name: MemberX500Name

    /**
     * Member's session initiation key.
     */
    val sessionInitiationKey: PublicKey

    /**
     * List of current and previous (rotated) ledger keys, which member can still use to sign unspent
     * transactions on ledger.
     * Key at index 0 is always the latest added ledger key.
     */
    val ledgerKeys: List<PublicKey>

    /**
     * Corda platform version that the member is running on.
     */
    val platformVersion: Int

    /**
     * An arbitrary number incremented each time the [MemberInfo] is changed.
     */
    val serial: Long

    /**
     * True if the member is active. Otherwise, false.
     */
    val isActive: Boolean
}
