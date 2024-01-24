package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TestWrappingRepository(
    val secondLevelWrappingKey: WrappingKeyInfo,
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : WrappingRepository {

    init {
        saveKey(secondLevelWrappingKey)
    }

    override fun saveKey(key: WrappingKeyInfo): WrappingKeyInfo {
        keys[key.alias] = key
        return key
    }

    override fun saveKeyWithId(key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo = TODO("Not needed")

    override fun findKey(alias: String): WrappingKeyInfo? = keys[alias]

    override fun findKeyAndId(alias: String): Pair<UUID, WrappingKeyInfo>? = TODO("Not needed")

    override fun findKeysWrappedByParentKey(parentKeyAlias: String): List<WrappingKeyInfo> = TODO("Not needed")

    override fun getKeyById(id: UUID): WrappingKeyInfo? = TODO("Not needed")

    override fun close() {
    }
}
