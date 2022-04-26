package net.corda.crypto.persistence

import net.corda.lifecycle.Lifecycle

interface SigningKeyCacheProvider : Lifecycle {
    fun getInstance(): SigningKeyCache
}