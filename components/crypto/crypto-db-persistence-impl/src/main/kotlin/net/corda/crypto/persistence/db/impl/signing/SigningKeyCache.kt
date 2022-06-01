package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import net.corda.crypto.persistence.signing.SigningCachedKey
import javax.persistence.EntityManagerFactory

class SigningKeyCache(
    val tenantId: String,
    val entityManagerFactory: EntityManagerFactory,
    val keys: Cache<String, SigningCachedKey>
) {
    fun clean() {
        keys.invalidateAll()
        keys.cleanUp()
    }
}
