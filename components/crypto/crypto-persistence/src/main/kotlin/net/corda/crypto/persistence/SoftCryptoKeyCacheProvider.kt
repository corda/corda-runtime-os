package net.corda.crypto.persistence

import net.corda.lifecycle.Lifecycle

interface SoftCryptoKeyCacheProvider : Lifecycle {
    fun getInstance(passphrase: String?, salt: String?): SoftCryptoKeyCache
}