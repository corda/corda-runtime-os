package net.corda.messaging.rocks

import net.corda.messaging.api.subscription.data.TopicData
import net.corda.messaging.subscription.factory.TopicDataFactory

/**
 * In-memory solution for TopicDataFactory
 */
class SimpleTopicDataFactoryImpl<K: Any, V: Any> : TopicDataFactory<K, V> {
    override fun create(): TopicData<K, V> {
        return SimpleTopicDataImpl()
    }

    override fun destroy(topicData: TopicData<K, V>) {
        topicData.clear()
    }
}
