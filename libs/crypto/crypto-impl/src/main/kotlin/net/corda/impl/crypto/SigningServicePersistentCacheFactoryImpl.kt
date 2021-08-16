package net.corda.impl.crypto

import org.hibernate.SessionFactory

class SigningServicePersistentCacheFactoryImpl(
    private val sessionFactory: () -> SessionFactory
) : SigningServicePersistentCacheFactory {
    override fun create(): SigningServicePersistentCache = SigningServicePersistentCacheImpl(
        sessionFactory()
    )
}