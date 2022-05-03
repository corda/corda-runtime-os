package net.corda.v5.application.membership

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.GroupParameters

/**
 * Service for retrieving group parameters used for resolving transactions according to parameters that were historically in force in the
 * membership group.
 */
@DoNotImplement
interface GroupParametersService {

    /**
     * [SecureHash] of the current parameters for the group.
     *
     * @return The [SecureHash] of the current parameters for the group.
     */
    val currentHash: SecureHash

    /**
     * Return the group parameters with the given [hash], or null if it doesn't exist.
     *
     * @return The [GroupParameters] that matches the input [hash], or null if it doesn't exist.
     */
    @Suspendable
    fun lookup(hash: SecureHash): GroupParameters?
}