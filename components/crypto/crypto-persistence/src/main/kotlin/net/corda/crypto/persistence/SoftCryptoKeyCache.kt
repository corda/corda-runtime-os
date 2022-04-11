package net.corda.crypto.persistence

interface SoftCryptoKeyCache : AutoCloseable {
    fun act(): SoftCryptoKeyCacheActions
    fun <R> act(block: (SoftCryptoKeyCacheActions) -> R): R {
        return act().use(block)
    }
}

