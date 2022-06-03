package net.corda.crypto.persistence.signing

import net.corda.lifecycle.Lifecycle

interface SigningKeyStoreProvider : Lifecycle {
    fun getInstance(): SigningKeyStore
}