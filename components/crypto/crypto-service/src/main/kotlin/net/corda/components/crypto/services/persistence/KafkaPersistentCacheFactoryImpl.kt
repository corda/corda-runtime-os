package net.corda.components.crypto.services.persistence

import net.corda.cipher.suite.impl.config.CryptoCacheConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PersistentCacheFactory::class])
class KafkaPersistentCacheFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : PersistentCacheFactory {
    override fun createSigningPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo> {
        return KafkaPersistentCacheImpl(
            SigningPersistentKeyInfo::class.java,
            subscriptionFactory,
            publisherFactory,
            config
        )
    }

    override fun createDefaultCryptoPersistentCache(
        config: CryptoCacheConfig
    ): PersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> {
        return KafkaPersistentCacheImpl(
            DefaultCryptoPersistentKeyInfo::class.java,
            subscriptionFactory,
            publisherFactory,
            config
        )
    }
}

