package net.corda.messaging.emulation.topic.service.impl

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemoryConfiguration
import net.corda.messaging.emulation.topic.model.ConsumerDefinitions
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.model.Topics
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Component

@Component
class TopicServiceImpl(
    private val config: InMemoryConfiguration = InMemoryConfiguration(),
    private val topics: Topics = Topics(config)
) : TopicService {

    override fun subscribe(consumerDefinitions: ConsumerDefinitions): Consumption {
        return topics.createConsumption(consumerDefinitions)
            .also {
                it.start()
            }
    }

    override fun addRecords(records: List<Record<*, *>>) {
        topics.getWriteLock(records).write {
            records.groupBy { record ->
                record.topic
            }.mapKeys {
                topics.getTopic(it.key)
            }.forEach { (topic, records) ->
                records.forEach {
                    topic.addRecord(it)
                }
            }
        }
    }

    override fun addRecordsToPartition(records: List<Record<*, *>>, partition: Int) {
        topics.getWriteLock(records, partition).write {
            records.groupBy { record ->
                record.topic
            }.mapKeys {
                topics.getTopic(it.key)
            }.forEach { (topic, records) ->
                records.forEach {
                    topic.addRecordToPartition(it, partition)
                }
            }
        }
    }

    override fun handleAllRecords(topicName: String, handler: (Sequence<RecordMetadata>) -> Unit) {
        topics.getTopic(topicName).handleAllRecords(handler)
    }
}
