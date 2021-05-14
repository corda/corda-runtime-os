package net.corda.messaging.impl.subscription.subscriptions.pubsub.service

import net.corda.messaging.api.records.Record
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.model.OffsetStrategy

interface TopicService {
    fun addRecords(records: List<Record<*, *>>)
    fun getRecords(topicName: String, consumerGroup: String, numberOfRecords: Int) : List<Record<*, *>>
    fun subscribe(topicName: String, consumerGroup: String, offsetStrategy: OffsetStrategy)
    fun unsubscribe(topicName: String, consumerGroup: String)
}
