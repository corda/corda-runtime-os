package net.corda.membership.registration

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.virtualnode.HoldingIdentity
import java.util.UUID

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
     * @param registrationId The registration ID
     * @param member The holding identity of the virtual node requesting registration.
     * @param context The member or MGM context required for on-boarding within a group.
     *
     * @throws [MembershipRegistrationException] in case of a registration error.
     *
     */
    fun register(
        registrationId: UUID,
        member: HoldingIdentity,
        context: Map<String, String>
    )
}