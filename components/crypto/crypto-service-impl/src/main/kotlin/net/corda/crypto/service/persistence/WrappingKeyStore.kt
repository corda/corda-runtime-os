package net.corda.crypto.service.persistence

import net.corda.lifecycle.Lifecycle

interface WrappingKeyStore : Lifecycle {
    fun saveWrappingKey(key: WrappingKeyInfo)
    fun findWrappingKey(alias: String): WrappingKeyInfo?
}

