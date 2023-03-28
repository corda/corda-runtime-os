package net.corda.crypto.persistence

import net.corda.crypto.core.ShortHash
import net.corda.v5.crypto.SecureHash
import java.time.Instant

/**
 * The main data transfer object type
 * to and from the [WrappingRepository].
 */
@Suppress("LongParameterList")
class SigningKeyInfo(
    val id: ShortHash,
    val fullId: SecureHash,
    val tenantId: String,
    val category: String,
    val alias: String?,
    val hsmAlias: String?,
    val publicKey: ByteArray,
    val keyMaterial: ByteArray?,
    val schemeCodeName: String,
    val masterKeyAlias: String?,
    val externalId: String?,
    val encodingVersion: Int?,
    val timestamp: Instant,
    val hsmId: String,
    val status: SigningKeyStatus,
)