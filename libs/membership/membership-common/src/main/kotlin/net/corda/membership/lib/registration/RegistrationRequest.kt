package net.corda.membership.lib.registration

import net.corda.data.membership.common.RegistrationStatus
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer

/**
 * Internal representation of a registration request.
 */
data class RegistrationRequest(
    val status: RegistrationStatus,
    val registrationId: String,
    val requester: HoldingIdentity,
    val memberContext: LayeredPropertyMap,
    val publicKey: ByteBuffer,
    val signature: ByteBuffer
)
