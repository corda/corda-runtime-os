package net.corda.messaging.impl.topic.service.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.impl.properties.InMemProperties.Companion.TOPICS_MAX_SIZE
import net.corda.messaging.impl.topic.service.TopicService
import net.corda.messaging.impl.topic.model.OffsetStrategy
import net.corda.messaging.impl.topic.model.RecordMetadata
import net.corda.messaging.impl.topic.model.Topic
import org.osgi.service.component.annotations.Component
import java.util.concurrent.*
import kotlin.concurrent.withLock

@Component
class TopicServiceImpl : TopicService {
    //TODO - replace with config service injection
    private val config: Config = ConfigFactory.load("tmpInMemDefaults")
    private val topicMaxSize = config.getInt(TOPICS_MAX_SIZE)
    private val topics: ConcurrentHashMap<String, Topic> = ConcurrentHashMap()

    override fun subscribe(topicName: String, consumerGroup: String, offsetStrategy: OffsetStrategy) {
        topics.putIfAbsent(topicName, Topic(topicName, topicMaxSize))
        val topic = topics[topicName]
        topic!!.lock.withLock {
            topic.subscribe(consumerGroup, offsetStrategy)
        }
    }

    override fun unsubscribe(topicName: String, consumerGroup: String) {
        topics[topicName]?.lock?.withLock {
            topics[topicName]?.unsubscribe(consumerGroup)
        }
    }

    override fun addRecords(records: List<Record<*, *>>) {
        val sortedTopics = records.map { record ->
            record.topic
        }.toHashSet().sorted()

        val topicLocks = sortedTopics.map { topicName ->
            topics.putIfAbsent(topicName, Topic(topicName, topicMaxSize))
            topics[topicName]!!.lock
        }

        try {
            topicLocks.forEach { it.lock() }
            records.forEach {
                val recordTopic = it.topic
                topics[recordTopic]!!.addRecord(it)
            }
        } finally {
            topicLocks.forEach {
                if (it.isHeldByCurrentThread) {
                    it.unlock()
                }
            }
        }
    }

    override fun getRecords(
        topicName: String,
        consumerGroup: String,
        numberOfRecords: Int,
        autoCommitOffset: Boolean
    ): List<RecordMetadata> {
        return topics[topicName]!!.lock.withLock {
            topics[topicName]!!.getRecords(consumerGroup, numberOfRecords, autoCommitOffset)
        }
    }

    override fun commitOffset(topicName: String, consumerGroup: String, offset: Long) {
        topics[topicName]!!.commitOffset(consumerGroup, offset)
    }
}
