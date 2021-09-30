package net.corda.crypto.impl.caching


class SimplePersistentCacheFactoryImpl<V, E>(
    private val entityClazz: Class<E>,
    private val sessionFactory: () -> Any
) : SimplePersistentCacheFactory<V, E> {
    override fun create(): SimplePersistentCache<V, E> = SimplePersistentCacheImpl(
        entityClazz,
        sessionFactory()
    )
}

