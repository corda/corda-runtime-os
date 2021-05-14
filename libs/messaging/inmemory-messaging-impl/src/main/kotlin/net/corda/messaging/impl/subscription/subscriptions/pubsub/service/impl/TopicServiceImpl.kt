package net.corda.messaging.impl.subscription.subscriptions.pubsub.service.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.impl.properties.InMemProperties.Companion.TOPICS_MAX_SIZE
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.TopicService
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.model.OffsetStrategy
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.model.Topic
import org.osgi.service.component.annotations.Component
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.concurrent.withLock

@Component
class TopicServiceImpl : TopicService {
    //TODO - replace with config service injection
    private val config: Config = ConfigFactory.load("tmpInMemDefaults")
    private val topicMaxSize = config.getInt(TOPICS_MAX_SIZE)
    private val locks : ConcurrentMap<String, ReentrantLock> = ConcurrentHashMap()
    private val topics : HashMap<String, Topic> = HashMap()

    override fun subscribe(topicName: String, consumerGroup: String, offsetStrategy: OffsetStrategy) {
        locks.putIfAbsent(topicName, ReentrantLock())
        locks[topicName]!!.withLock {
            val topic = topics.getOrPut(topicName, {Topic(topicName, topicMaxSize)})
            topic.subscribe(consumerGroup, offsetStrategy)
        }
    }

    override fun unsubscribe(topicName: String, consumerGroup: String) {
        locks[topicName]?.withLock {
            topics[topicName]?.unsubscribe(consumerGroup)
        }
    }

    override fun addRecords(records: List<Record<*, *>>) {
        val recordTopics = records.map { record ->
            record.topic
        }.toHashSet()

        val sortedTopics = recordTopics.sorted()

        val topicLocks = sortedTopics.map{ topicName ->
            locks.putIfAbsent(topicName, ReentrantLock())
            locks[topicName]
        }

        try {
            topicLocks.forEach { it!!.lock() }
            records.forEach {
                val recordTopic = it.topic
                topics.putIfAbsent(recordTopic, Topic(recordTopic, topicMaxSize))
                topics[recordTopic]!!.addRecord(it)
            }
        } finally {
            topicLocks.forEach {
                if (it!!.isHeldByCurrentThread) {
                    it.unlock()
                }
            }
        }
    }

    override fun getRecords(topicName: String, consumerGroup: String, numberOfRecords: Int): List<Record<*, *>> {
        var records : List<Record<*, *>>
        locks.putIfAbsent(topicName, ReentrantLock())
        locks[topicName]!!.withLock {
            records = topics[topicName]!!.getRecords(consumerGroup, numberOfRecords)
        }

        return records
    }
}
