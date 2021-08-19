package net.corda.crypto.impl

import net.corda.crypto.impl.caching.SimplePersistentCacheImpl
import org.hibernate.SessionFactory

class SigningServicePersistentCacheImpl(
    sessionFactory: SessionFactory,
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
        sessionFactory.openSession().use { session ->
            val query = session.createQuery("from ${SigningPersistentKey::class.java.simpleName} e where e.alias = :alias")
            query.setParameter("alias", alias)
            return query.singleResult as? SigningPersistentKey
        }
    }
}