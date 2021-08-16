package net.corda.impl.caching.crypto

import org.hibernate.SessionFactory

class SimplePersistentCacheFactoryImpl<V, E>(
        private val entityClazz: Class<E>,
        private val sessionFactory: () -> SessionFactory
): SimplePersistentCacheFactory<V, E> {
    override fun create(): SimplePersistentCache<V, E> = SimplePersistentCacheImpl(
            entityClazz,
            sessionFactory()
    )
}

