package net.corda.crypto.service.impl.infra

import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.SigningRepository

class TestWrappingRepository(
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : SigningRepository {
    override fun saveKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findKey(alias: String): WrappingKeyInfo? = keys[alias]

    override fun close() {
    }
}