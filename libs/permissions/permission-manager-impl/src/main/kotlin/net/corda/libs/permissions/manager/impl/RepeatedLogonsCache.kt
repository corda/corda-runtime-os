package net.corda.libs.permissions.manager.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.v5.crypto.sha256Bytes
import java.time.Duration

class RepeatedLogonsCache {

    private val maximumSize =
        java.lang.Long.getLong("net.corda.libs.permissions.manager.repeatedLogons.cache.size", 1000)

    private val expireAfterWriteSeconds =
        java.lang.Long.getLong("net.corda.libs.permissions.manager.repeatedLogons.expire.seconds", 600)

    private val cache: Cache<String, ByteArray> = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterWrite(Duration.ofSeconds(expireAfterWriteSeconds))
        .build()

    private fun String.toCacheValue(): ByteArray {
        return toByteArray().sha256Bytes()
    }

    fun add(loginName: String, clearTextPassword: String) {
        cache.put(loginName, clearTextPassword.toCacheValue())
    }

    fun remove(loginName: String) {
        cache.invalidate(loginName)
    }

    fun verifies(loginName: String, clearTextPassword: String): Boolean {
        val cachedValue = cache.getIfPresent(loginName) ?: return false
        return clearTextPassword.toCacheValue().contentEquals(cachedValue)
    }
}
