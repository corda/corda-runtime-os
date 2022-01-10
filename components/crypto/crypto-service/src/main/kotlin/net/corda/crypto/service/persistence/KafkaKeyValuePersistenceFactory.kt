package net.corda.crypto.service.persistence

import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.defaultCryptoService
import net.corda.crypto.impl.config.publicKeys
import net.corda.crypto.impl.persistence.SoftCryptoKeyRecordInfo
import net.corda.crypto.impl.persistence.SoftCryptoKeyRecord
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningKeyRecord
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
        config = config.publicKeys
    )

    private val cryptoProxy = KafkaDefaultCryptoKeyProxy(
        subscriptionFactory = subscriptionFactory,
        publisherFactory = publisherFactory,
        config = config.defaultCryptoService
    )

    override fun createSigningPersistence(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeyRecord, SigningKeyRecord>
    ): KeyValuePersistence<SigningKeyRecord, SigningKeyRecord> {
        return KafkaKeyValuePersistence(
            proxy = signingProxy,
            memberId = tenantId,
            config = config.publicKeys,
            mutator = mutator
        )
    }

    override fun createDefaultCryptoPersistence(
        tenantId: String,
        mutator: KeyValueMutator<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
    ): KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord> {
        return KafkaKeyValuePersistence(
            proxy = cryptoProxy,
            memberId = tenantId,
            config = config.defaultCryptoService,
            mutator = mutator
        )
    }

    override fun close() {
        signingProxy.closeGracefully()
        cryptoProxy.closeGracefully()
    }
}

