package net.corda.crypto.service

import net.corda.crypto.core.ShortHash
import java.time.Instant

@Suppress("LongParameterList")
class SigningKeyInfo(
    val id: ShortHash,
    // TODO see if we need to add full id here
    val tenantId: String,
    val category: String,
    val alias: String?,
    val hsmAlias: String?,
    val publicKey: ByteArray,
    val schemeCodeName: String,
    val masterKeyAlias: String?,
    val externalId: String?,
    val encodingVersion: Int?,
    val created: Instant
)