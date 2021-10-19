package net.corda.crypto.service.persistence

import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.keyCache
import net.corda.crypto.impl.config.mngCache
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig

// The subscription implementations are keeping the full data, so no need to process it. Also, as in general case,
// we need to mutate the entity to some meaningful values for performance’s sake, like converting binary arrays to
// public/private keys as that expensive operations.
class KafkaKeyValuePersistenceFactory constructor(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    private val config: CryptoLibraryConfig,
) : KeyValuePersistenceFactory, AutoCloseable {

    private val signingProxy = KafkaSigningKeyProxy(
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        config = config.mngCache
    )

    private val cryptoProxy = KafkaDefaultCryptoKeyProxy(
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        config = config.keyCache
    )

    override fun createSigningPersistence(
        memberId: String,
        mutator: KeyValueMutator<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    ): KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo> {
        return KafkaKeyValuePersistence(
            proxy = signingProxy,
            memberId = memberId,
            config = config.mngCache,
            mutator = mutator
        )
    }

    override fun createDefaultCryptoPersistence(
        memberId: String,
        mutator: KeyValueMutator<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
    ): KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> {
        return KafkaKeyValuePersistence(
            proxy = cryptoProxy,
            memberId = memberId,
            config = config.keyCache,
            mutator = mutator
        )
    }

    override fun close() {
        signingProxy.closeGracefully()
        cryptoProxy.closeGracefully()
    }
}

