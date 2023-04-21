package net.corda.messaging.emulation.topic.service.impl

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemoryConfiguration
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.Topics
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Component

@Component(service = [TopicService::class])
class TopicServiceImpl(
    private val config: InMemoryConfiguration = InMemoryConfiguration(),
    private val topics: Topics = Topics(config)
) : TopicService {

    override fun createConsumption(consumer: Consumer): Consumption {
        return topics.createConsumption(consumer)
            .also {
                it.start()
            }
    }

    override fun manualAssignPartitions(consumer: Consumer, partitionsIds: Collection<Int>) {
        topics.getTopic(consumer.topicName).assignPartition(consumer, partitionsIds)
    }

    override fun manualUnAssignPartitions(consumer: Consumer, partitionsIds: Collection<Int>) {
        topics.getTopic(consumer.topicName).unAssignPartition(consumer, partitionsIds)
    }

    override fun getLatestOffsets(topicName: String): Map<Int, Long> {
        return topics.getLatestOffsets(topicName)
    }

    override fun addRecords(records: List<Record<*, *>>) {
        val topicToRecords = records.groupBy { record ->
            record.topic
        }.mapKeys {
            topics.getTopic(it.key)
        }

        topics.getWriteLock(records).write {
            topicToRecords.forEach { (topic, records) ->
                records.forEach {
                    topic.addRecord(it)
                }
            }
        }

        topicToRecords.keys.forEach {
            it.wakeUpConsumers()
        }
    }

    override fun addRecordsToPartition(records: List<Record<*, *>>, partition: Int) {
        val topicToRecords = records.groupBy { record ->
            record.topic
        }.mapKeys {
            topics.getTopic(it.key)
        }
        topics.getWriteLock(records, partition).write {
            topicToRecords.forEach { (topic, records) ->
                records.forEach {
                    topic.addRecordToPartition(it, partition)
                }
            }
        }
        topicToRecords.keys.forEach {
            it.wakeUpConsumers()
        }
    }
}
