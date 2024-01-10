package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TestWrappingRepository(
    val secondLevelWrappingKey: WrappingKeyInfo,
    val secondLevelWrappingKeyAlias: String = "second",
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : WrappingRepository {

    init {
        saveKey(secondLevelWrappingKeyAlias, secondLevelWrappingKey)
    }

    override fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo {
        keys[alias] = key
        return key
    }

    override fun saveKeyWithId(alias: String, key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo = TODO("Not needed")

    override fun findKey(alias: String): WrappingKeyInfo? = keys[alias]

    override fun findKeyAndId(alias: String): Pair<UUID, WrappingKeyInfo>? = TODO("Not needed")

    override fun close() {
    }
}