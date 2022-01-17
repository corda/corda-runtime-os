package net.corda.crypto.component.persistence

import java.security.PrivateKey
import java.security.PublicKey

class SoftKeysRecordInfo(
    val tenantId: String,
    val publicKey: PublicKey? = null,
    var privateKey: PrivateKey? = null,
    var wrappingKey: WrappingKey? = null
)