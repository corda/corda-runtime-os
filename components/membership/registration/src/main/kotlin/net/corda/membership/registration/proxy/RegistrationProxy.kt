package net.corda.membership.registration.proxy

import net.corda.lifecycle.Lifecycle
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.virtualnode.HoldingIdentity

/**
 * Proxy for registering a holding identity with the appropriate instance of [MemberRegistrationService].
 * Implementations of this interface must coordinate lifecycles of all [MemberRegistrationService]s.
 */
interface RegistrationProxy : Lifecycle {
    /**
     * Retrieves the appropriate instance of [MemberRegistrationService] for a holding identity as specified in the CPI
     * configuration, and forwards the registration request to it.
     *
     * @param member The holding identity of the virtual node requesting registration.
     *
     * @return The status of the registration request as reported by the [MemberRegistrationService].
     * NOT_SUBMITTED is returned if something goes wrong while creating the request.
     */
    fun register(member: HoldingIdentity): MembershipRequestRegistrationResult
}
