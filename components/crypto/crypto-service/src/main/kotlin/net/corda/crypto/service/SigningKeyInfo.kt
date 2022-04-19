package net.corda.crypto.service

import java.time.Instant

@Suppress("LongParameterList")
class SigningKeyInfo(
    val id: String,
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