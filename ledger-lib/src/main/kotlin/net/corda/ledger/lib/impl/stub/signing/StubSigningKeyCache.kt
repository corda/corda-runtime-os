package net.corda.ledger.lib.impl.stub.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.flow.application.crypto.MySigningKeysCache
import net.corda.ledger.lib.common.Constants.CACHE_MAX_SIZE
import java.security.PublicKey

class StubSigningKeyCache : MySigningKeysCache {

    private data class CacheValue(val publicKey: PublicKey?)

    private val cache: Cache<PublicKey, CacheValue> = CacheFactoryImpl().build(
        "My-Signing-Key-Cache",
        Caffeine.newBuilder().maximumSize(CACHE_MAX_SIZE)
    )

    override fun get(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        return if (keys.isNotEmpty()) {
            cache.getAllPresent(keys.map { it })
                .map { (key, value) -> key to value.publicKey }
                .toMap()
        } else {
            emptyMap()
        }
    }

    override fun putAll(keys: Map<out PublicKey, PublicKey?>) {
        if (keys.isNotEmpty()) {
            cache.putAll(keys.map { (key, value) -> key to CacheValue(value) }.toMap())
        }
    }
}