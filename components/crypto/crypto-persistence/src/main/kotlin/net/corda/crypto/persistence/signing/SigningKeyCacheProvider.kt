package net.corda.crypto.persistence.signing

import net.corda.lifecycle.Lifecycle

interface SigningKeyCacheProvider : Lifecycle {
    fun getInstance(): SigningKeyCache
}