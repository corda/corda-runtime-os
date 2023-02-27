package net.corda.membership.lib.registration

import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationStatus
import java.time.Instant

/**
 * Internal representation of a registration request status.
 */
data class RegistrationRequestStatus(
    val status: RegistrationStatus,
    val registrationId: String,
    val memberContext: KeyValuePairList,
    val registrationSent: Instant,
    val registrationLastModified: Instant,
    val protocolVersion: Int,
    val reason: String? = null,
)
