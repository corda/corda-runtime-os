package net.corda.crypto.impl

class SigningServicePersistentCacheFactoryImpl(
    private val sessionFactory: () -> Any
) : SigningServicePersistentCacheFactory {
    override fun create(): SigningServicePersistentCache = SigningServicePersistentCacheImpl(
        sessionFactory()
    )
}