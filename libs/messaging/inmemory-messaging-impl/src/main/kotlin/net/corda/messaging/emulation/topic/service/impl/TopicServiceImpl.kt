package net.corda.messaging.emulation.topic.service.impl

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemoryConfiguration
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.Topics
import net.corda.messaging.emulation.topic.service.TopicService
import org.osgi.service.component.annotations.Component

@Component
class TopicServiceImpl(
    private val config: InMemoryConfiguration = InMemoryConfiguration(),
    private val topics: Topics = Topics(config)
) : TopicService {

    override fun subscribe(consumer: Consumer): Lifecycle {
        return topics.createConsumerThread(consumer).also {
            it.start()
        }
    }

    override fun addRecords(records: List<Record<*, *>>) {
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

    override fun addRecordsToPartition(records: List<Record<*, *>>, partition: Int) {
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
