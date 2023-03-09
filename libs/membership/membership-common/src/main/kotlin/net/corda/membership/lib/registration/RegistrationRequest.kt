package net.corda.membership.lib.registration

import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.RegistrationStatus
import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer

/**
 * Internal representation of a registration request.
 */
data class RegistrationRequest(
    val status: RegistrationStatus,
    val registrationId: String,
    val requester: HoldingIdentity,
    val memberContext: ByteBuffer,
    val signature: CryptoSignatureWithKey,
    val serial: Long?,
)
