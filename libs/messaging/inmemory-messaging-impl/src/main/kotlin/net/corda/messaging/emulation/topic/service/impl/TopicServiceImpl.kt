package net.corda.messaging.emulation.topic.service.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemProperties.Companion.TOPICS_MAX_SIZE
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.model.Topic
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock

@Component
class TopicServiceImpl : TopicService {
    //TODO - replace with config service injection
    private val config: Config = ConfigFactory.load("tmpInMemDefaults")
    private val topicMaxSize = config.getInt(TOPICS_MAX_SIZE)
    private val topics: ConcurrentHashMap<String, Topic> = ConcurrentHashMap()

    override fun subscribe(topicName: String, consumerGroup: String, offsetStrategy: OffsetStrategy) {
        val topic = topics.computeIfAbsent(topicName) {
            Topic(topicName, topicMaxSize)
        }
        topic.lock.withLock {
            topic.subscribe(consumerGroup, offsetStrategy)
        }
    }

    override fun addRecords(records: List<Record<*, *>>) {
        val sortedTopics = records.map { record ->
            record.topic
        }.toHashSet().sorted()

        val topicLocks = sortedTopics.map { topicName ->
            topics.computeIfAbsent(topicName) { Topic(topicName, topicMaxSize) }.lock
        }

        try {
            topicLocks.forEach { it.lock() }
            records.forEach {
                val topic = topics[it.topic] ?: throw CordaMessageAPIFatalException("Topic Does not exist")
                topic.addRecord(it)
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
        val topic = topics[topicName] ?: throw CordaMessageAPIFatalException("Topic Does not exist")
        return topic.lock.withLock {
            topic.getRecords(consumerGroup, numberOfRecords, autoCommitOffset)
        }
    }

    override fun commitOffset(topicName: String, consumerGroup: String, offset: Long) {
        val topic = topics[topicName] ?: throw CordaMessageAPIFatalException("Topic Does not exist")
        topic.commitOffset(consumerGroup, offset)
    }
}
