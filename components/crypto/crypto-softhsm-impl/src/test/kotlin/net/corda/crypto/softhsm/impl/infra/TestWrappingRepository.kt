package net.corda.crypto.softhsm.impl.infra

import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository

class TestWrappingRepository(
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : WrappingRepository {
    val findCounter = mutableMapOf<String, Int>()

    override fun saveKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findKey(alias: String): WrappingKeyInfo? {
        findCounter[alias] = findCounter[alias]?.plus(1) ?: 1
        return keys[alias]
    }

    override fun close() {
    }
}