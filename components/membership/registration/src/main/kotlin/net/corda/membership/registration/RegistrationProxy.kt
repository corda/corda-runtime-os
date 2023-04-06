package net.corda.membership.registration

import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.exceptions.RegistrationProtocolSelectionException
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.HoldingIdentity
import java.util.UUID
import kotlin.jvm.Throws

/**
 * Proxy for registering a holding identity with the appropriate instance of [MemberRegistrationService].
 * Implementations of this interface must coordinate lifecycles of all [MemberRegistrationService]s.
 */
interface RegistrationProxy : Lifecycle {
    /**
     * Retrieves the appropriate instance of [MemberRegistrationService] for a holding identity as specified in the CPI
     * configuration, and forwards the registration request to it.
     *
     * @param registrationId The registration ID.
     * @param member The holding identity of the virtual node requesting registration.
     * @param context The member or MGM context required for on-boarding within a group.
     *
     * @return The status of the registration request as reported by the [MemberRegistrationService].
     * NOT_SUBMITTED is returned if something goes wrong while creating the request.
     *
     * @throws [RegistrationProtocolSelectionException] when the registration protocol could not be selected.
     * @throws [NotReadyMembershipRegistrationException] when the registration fail
     *     because a service might not be ready.
     * @throws [InvalidMembershipRegistrationException] when the registration fail because it is invalid.
     */
    @Throws(
        NotReadyMembershipRegistrationException::class,
        InvalidMembershipRegistrationException::class,
        RegistrationProtocolSelectionException::class,
    )
    fun register(
        registrationId: UUID,
        member: HoldingIdentity,
        context: Map<String, String>,
    ): Collection<Record<*, *>>
}
