package net.corda.crypto.persistence.soft

import net.corda.lifecycle.Lifecycle

interface SoftCryptoKeyCacheProvider : Lifecycle {
    fun getInstance(): SoftCryptoKeyCache
}