package net.corda.crypto.persistence.soft

interface SoftCryptoKeyStore : AutoCloseable {
    fun act(): SoftCryptoKeyStoreActions
    fun <R> act(block: (SoftCryptoKeyStoreActions) -> R): R {
        return act().use(block)
    }
}

