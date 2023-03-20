package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.CryptoRepository
import java.util.concurrent.ConcurrentHashMap

class TestCryptoRepository(
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : CryptoRepository {
    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findWrappingKey(alias: String): WrappingKeyInfo? = keys[alias]
    override fun close() {
    }
}