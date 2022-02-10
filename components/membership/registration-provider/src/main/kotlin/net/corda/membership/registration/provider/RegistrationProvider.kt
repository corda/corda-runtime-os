package net.corda.membership.registration.provider

import net.corda.lifecycle.Lifecycle
import net.corda.membership.registration.MemberRegistrationService
import net.corda.virtualnode.HoldingIdentity

/**
 * API for retrieving the appropriate instance of [MemberRegistrationService] for a holding identity.
 * Implementations of this provider must coordinate lifecycle of all [MemberRegistrationService]s.
 */
interface RegistrationProvider : Lifecycle {

    /**
     * Gets an instance of [MemberRegistrationService] for a holding identity as specified by CPI configuration.
     *
     * @param holdingIdentity [HoldingIdentity] of the tenant.
     *
     * @return An instance of [MemberRegistrationService] for the given tenant.
     */
    fun get(holdingIdentity: HoldingIdentity): MemberRegistrationService
}