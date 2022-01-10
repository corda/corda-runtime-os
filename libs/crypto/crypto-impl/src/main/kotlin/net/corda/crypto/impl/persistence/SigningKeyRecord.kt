package net.corda.crypto.impl.persistence

import java.util.UUID

@Suppress("LongParameterList")
class SigningKeyRecord(
    var tenantId: String,
    var category: String,
    var alias: String?,
    var hsmAlias: String?,
    var publicKey: ByteArray,
    var privateKeyMaterial: ByteArray?,
    var schemeCodeName: String,
    var masterKeyAlias: String?,
    var externalId: UUID?,
    var version: Int = 1
)