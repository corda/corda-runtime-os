package net.corda.messaging.emulation.topic.service.impl

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.TopicsConfiguration
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.ConsumerThread
import net.corda.messaging.emulation.topic.model.Topic
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class TopicServiceImpl : TopicService {
    private val config = TopicsConfiguration()
    private val topics: ConcurrentHashMap<String, Topic> = ConcurrentHashMap()

    override fun subscribe(consumer: Consumer): LifeCycle {
        val topic = getTopic(consumer.topicName)
        val thread = ConsumerThread(consumer, topic)
        thread.start()
        return thread
    }

    private fun getTopic(topicName: String): Topic {
        return topics.computeIfAbsent(topicName) {
            Topic(topicName, config.configuration(topicName))
        }
    }

    override fun addRecords(records: List<Record<*, *>>) {
        records.groupBy { record ->
            record.topic
        }.mapKeys {
            getTopic(it.key)
        }.forEach { (topic, records) ->
            records.forEach {
                topic.addRecord(it)
            }
        }
    }
}
