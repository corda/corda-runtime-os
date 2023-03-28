package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import java.util.concurrent.ConcurrentHashMap

class TestWrappingRepository(
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : WrappingRepository {
    override fun saveKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findKey(alias: String): WrappingKeyInfo? = keys[alias]

    override fun close() {
    }
}