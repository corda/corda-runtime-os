package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TestWrappingRepository(
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : WrappingRepository {
    val findCounter = mutableMapOf<String, Int>()

    override fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo {
        keys[alias] = key
        return key
    }

    override fun saveKeyWithId(alias: String, key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo {
        keys[alias] = key
        return key
    }

    override fun findKey(alias: String): WrappingKeyInfo? {
        findCounter[alias] = findCounter[alias]?.plus(1) ?: 1
        return keys[alias]
    }

    override fun findKeyAndId(alias: String): Pair<UUID, WrappingKeyInfo>? {
        findCounter[alias] = findCounter[alias]?.plus(1) ?: 1
        return keys[alias]?.let {
            UUID.randomUUID() to it
        }
    } 
    
    override fun close() {
    }
}