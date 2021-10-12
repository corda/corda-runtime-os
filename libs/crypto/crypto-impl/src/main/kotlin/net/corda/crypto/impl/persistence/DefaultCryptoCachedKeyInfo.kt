package net.corda.crypto.impl.persistence

import java.security.PrivateKey
import java.security.PublicKey

class DefaultCryptoCachedKeyInfo(
    override val memberId: String,
    val publicKey: PublicKey? = null,
    var privateKey: PrivateKey? = null,
    var wrappingKey: WrappingKey? = null
) : IHaveMemberId