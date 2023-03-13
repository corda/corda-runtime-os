package net.corda.membership.staticnetwork

import net.corda.membership.lib.UnsignedGroupParameters
import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID

/**
 * Data class representation of static network info.
 *
 * Static networks are a development convenience for quickly bringing up networks to test CorDapps without an MGM. This
 * is why [PrivateKey]s are stored in this data type without concern of security breaches.
 */
data class StaticNetworkInfo(
    val groupId: UUID,
    val mgmSigningPublicKey: PublicKey,
    val mgmSigningPrivateKey: PrivateKey,
    val groupParameters: UnsignedGroupParameters,
    val version: Int
)