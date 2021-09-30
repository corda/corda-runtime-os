package net.corda.crypto.impl

import net.corda.crypto.impl.caching.SimplePersistentCacheImpl

class SigningServicePersistentCacheImpl(
    sessionFactory: Any,
    expireInMinutes: Long = 60,
    maxSize: Long = 1000
) : SimplePersistentCacheImpl<SigningPersistentKey, SigningPersistentKey>(
    SigningPersistentKey::class.java,
    sessionFactory,
    expireInMinutes,
    maxSize
), SigningServicePersistentCache {
    override fun put(key: Any, entity: SigningPersistentKey): SigningPersistentKey = put(key, entity) { it }
    override fun get(key: Any): SigningPersistentKey? = get(key) { it }
    override fun findByAlias(alias: Any): SigningPersistentKey? {
        return null
    }
}