package net.corda.membership.lib.registration

import net.corda.data.membership.SignedData
import net.corda.data.membership.common.RegistrationStatus
import net.corda.virtualnode.HoldingIdentity

/**
 * Internal representation of a registration request.
 */
data class RegistrationRequest(
    val status: RegistrationStatus,
    val registrationId: String,
    val requester: HoldingIdentity,
    val memberContext: SignedData,
    val registrationContext: SignedData,
    val serial: Long?,
)
