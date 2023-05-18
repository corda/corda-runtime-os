package net.corda.messaging.rocks

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.subscription.factory.TopicDataFactory

interface TopicDataFactoryBuilder {
    fun <K : Any, V : Any> create(
        config: SmartConfig,
        topic: String,
        keyClass: Class<K>,
        valueClass: Class<V>
    ): TopicDataFactory<K, V>
}
