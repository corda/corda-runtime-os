package net.corda.crypto.persistence.soft

import net.corda.lifecycle.Lifecycle

interface SoftCryptoKeyStoreProvider : Lifecycle {
    fun getInstance(): SoftCryptoKeyStore
}