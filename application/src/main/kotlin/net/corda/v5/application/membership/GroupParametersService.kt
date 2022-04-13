package net.corda.v5.application.membership

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.GroupParameters

/**
 * Service for retrieving group parameters used for resolving transactions according to parameters that were historically in force in the
 * membership group.
 */
@DoNotImplement
interface GroupParametersService : CordaServiceInjectable, CordaFlowInjectable {

    /**
     * [SecureHash] of the current parameters for the group.
     *
     * @return The [SecureHash] of the current parameters for the group.
     */
    val currentHash: SecureHash

    /**
     * For backwards compatibility, this parameters hash will be used for resolving historical transactions in the chain.
     *
     * @return The default hash.
     */
    val defaultHash: SecureHash

    /**
     * Return the group parameters with the given [hash], or null if it doesn't exist.
     *
     * @return The [GroupParameters] that matches the input [hash], or null if it doesn't exist.
     */
    fun lookup(hash: SecureHash): GroupParameters?
}