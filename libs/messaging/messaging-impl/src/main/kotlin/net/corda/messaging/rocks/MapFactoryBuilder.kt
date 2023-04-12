package net.corda.messaging.rocks

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.rocks.db.api.StorageManager

interface MapFactoryBuilder {
    fun <K : Any, V : Any> create(
        storageManager: StorageManager,
        config: SmartConfig,
        topic: String,
        keyClass: Class<K>,
        valueClass: Class<V>
    ): MapFactory<K, V>

    fun <K : Any, V : Any> createSimpleMapFactory(): MapFactory<K, V>
}