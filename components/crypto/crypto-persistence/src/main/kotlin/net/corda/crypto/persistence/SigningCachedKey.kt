package net.corda.crypto.persistence

import java.time.Instant

@Suppress("LongParameterList")
class SigningCachedKey(
    val id: String,
    // TODO See if we need to return the full key id
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
    val status: SigningKeyStatus
)