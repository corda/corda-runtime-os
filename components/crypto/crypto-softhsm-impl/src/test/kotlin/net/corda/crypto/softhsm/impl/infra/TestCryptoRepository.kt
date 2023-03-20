package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.CryptoRepository
import java.util.concurrent.ConcurrentHashMap

class TestCryptoRepository(
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : CryptoRepository {
    val findCounter = mutableMapOf<String, Int>()

    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findWrappingKey(alias: String): WrappingKeyInfo? {
        findCounter[alias] = findCounter[alias]?.plus(1) ?: 1
        return keys[alias]
    }

    override fun close() {
    }
}