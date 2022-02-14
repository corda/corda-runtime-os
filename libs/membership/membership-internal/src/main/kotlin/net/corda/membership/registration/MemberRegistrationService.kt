package net.corda.membership.registration

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.virtualnode.HoldingIdentity

enum class MembershipRequestRegistrationOutcome {
    /**
     * Registration request got submitted to the MGM successfully.
     */
    SUBMITTED,

    /**
     * Something went wrong and registration request was not sent to the MGM.
     */
    NOT_SUBMITTED
}

/**
 * Registration result returned after calling [MemberRegistrationService.register].
 *
 * @property outcome Enum value representing the outcome of the registration.
 * @property message Additional information, like reason for failed registration submission.
 */
data class MembershipRequestRegistrationResult(val outcome: MembershipRequestRegistrationOutcome, val message: String? = null)

/**
 * Handles the registration process on the member side.
 */
interface MemberRegistrationService : Lifecycle {
    /**
     * Lifecycle coordinator name for implementing services.
     *
     * All implementing services must make use of the optional `instanceId` parameter when creating their
     * [LifecycleCoordinatorName] so that multiple instances of [MemberRegistrationService] coordinator can be created
     * and followed by the registration provider.
     */
    val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName(MemberRegistrationService::class.java.name, this::class.java.name)

    /**
     * Creates the registration request and submits it towards the MGM.
     * This is the first step to take for a virtual node to become a fully
     * qualified member within a membership group.
     *
     * @param member The holding identity of the virtual node requesting registration.
     *
     * @return The status of the registration request. NOT_SUBMITTED is returned when
     * something went wrong during creating the request.
     */
    fun register(member: HoldingIdentity): MembershipRequestRegistrationResult
}