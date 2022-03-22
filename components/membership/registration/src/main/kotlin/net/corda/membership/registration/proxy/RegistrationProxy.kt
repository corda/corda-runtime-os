package net.corda.membership.registration.proxy

import net.corda.membership.registration.MemberRegistrationService

/**
 * API for registering a holding identity with the appropriate instance of [MemberRegistrationService].
 * Implementations of this proxy must coordinate lifecycles of all [MemberRegistrationService]s.
 */
interface RegistrationProxy : MemberRegistrationService
