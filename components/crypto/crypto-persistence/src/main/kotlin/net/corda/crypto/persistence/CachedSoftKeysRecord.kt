package net.corda.crypto.persistence

import java.security.PrivateKey
import java.security.PublicKey

class CachedSoftKeysRecord(
    val tenantId: String,
    val publicKey: PublicKey? = null,
    var privateKey: PrivateKey? = null,
    var wrappingKey: WrappingKey? = null
)