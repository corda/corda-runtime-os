package net.corda.libs.permissions.manager.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.core.sha256Bytes
import java.time.Duration

/**
 * Cache which is meant to improve performance of the repeated logon requests with Basic Authentication.
 *
 * To prevent clear text passwords to be stored in memory a fast hashing function (SHA-256) is applied to them.
 * This is meant to protect against an attack where an adversary obtains a memory dump of the running process
 * hoping to be able to read passwords cached passwords in the clear.
 */
internal class RepeatedLogonsCache {

    // Potentially those two parameters should be moved to the configuration schema.
    private val maximumSize =
        java.lang.Long.getLong("net.corda.libs.permissions.manager.repeatedLogons.cache.size", 1000)

    private val expireAfterWriteSeconds =
        java.lang.Long.getLong("net.corda.libs.permissions.manager.repeatedLogons.expire.seconds", 600)

    private val cache: Cache<String, ByteArray> = CacheFactoryImpl().build(
        javaClass.simpleName,
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(Duration.ofSeconds(expireAfterWriteSeconds))
    )

    private fun Pair<String, String>.toCacheValue(): ByteArray {
        // Using loginName and clearTextPassword password for hashing. Such that even if two users have exactly the same
        // password, their hashed representation will be different.
        return "$first:$second".toByteArray().sha256Bytes()
    }

    fun add(loginName: String, clearTextPassword: String) {
        cache.put(loginName, (loginName to clearTextPassword).toCacheValue())
    }

    fun remove(loginName: String) {
        cache.invalidate(loginName)
    }

    fun verifies(loginName: String, clearTextPassword: String): Boolean {
        val cachedValue = cache.getIfPresent(loginName) ?: return false
        // We are performing an exact comparison here to make sure that cached hash value is exactly the same
        // as the value produced from loginName/clearTextPassword pair supplied.
        return (loginName to clearTextPassword).toCacheValue().contentEquals(cachedValue)
    }
}
