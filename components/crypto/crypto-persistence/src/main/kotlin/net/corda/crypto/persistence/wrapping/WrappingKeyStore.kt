package net.corda.crypto.persistence.wrapping

import net.corda.lifecycle.Lifecycle

interface WrappingKeyStore : Lifecycle {
    fun saveWrappingKey(alias: String, key: WrappingKeyInfo)
    fun findWrappingKey(alias: String): WrappingKeyInfo?
}

