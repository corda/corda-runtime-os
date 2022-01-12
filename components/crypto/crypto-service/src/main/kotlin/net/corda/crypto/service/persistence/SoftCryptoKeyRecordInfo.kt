package net.corda.crypto.service.persistence

import java.security.PrivateKey
import java.security.PublicKey

class SoftCryptoKeyRecordInfo(
    val tenantId: String,
    val publicKey: PublicKey? = null,
    var privateKey: PrivateKey? = null,
    var wrappingKey: WrappingKey? = null
)