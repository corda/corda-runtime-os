package net.corda.membership.lib.registration

import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer

/**
 * Internal representation of a registration request.
 */
data class RegistrationRequest(
    val registrationId: String,
    val requester: HoldingIdentity,
    val memberContext: ByteBuffer,
    val publicKey: ByteBuffer,
    val signature: ByteBuffer
)