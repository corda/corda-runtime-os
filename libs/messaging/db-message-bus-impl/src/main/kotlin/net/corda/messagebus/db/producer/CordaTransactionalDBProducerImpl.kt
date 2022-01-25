package net.corda.messagebus.db.producer

import com.typesafe.config.Config
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.TRANSACTIONAL_ID
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.db.persistence.DBWriter
import net.corda.messagebus.db.persistence.RecordDbEntry
import net.corda.messagebus.db.toCordaRecord
import net.corda.messagebus.db.util.CordaProducerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.schema.registry.AvroSchemaRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.math.abs

class CordaTransactionalDBProducerImpl(
    private val schemaRegistry: AvroSchemaRegistry,
    private val config: Config,
    private val topicService: TopicService,
    private val dbWriterImpl: DBWriter
) : CordaProducer {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val transactionalId = config.getStringOrNull(TRANSACTIONAL_ID)
    private val defaultTimeout: Duration = Duration.ofSeconds(1)
    private val topicPartitionMap = dbWriterImpl.getTopicPartitionMap()

    private val transactionalRecords = ThreadLocal.withInitial { mutableListOf<RecordDbEntry>() }
    private val inTransaction: Boolean
        get() = transactionalRecords.get().isNotEmpty()

    override fun send(record: CordaProducerRecord<*, *>, callback: CordaProducer.Callback?) {
        sendRecords(listOf(record))
        callback?.onCompletion(null)
    }

    override fun send(record: CordaProducerRecord<*, *>, partition: Int, callback: CordaProducer.Callback?) {
        sendRecordsToPartitions(listOf(Pair(partition, record)))
        callback?.onCompletion(null)
    }

    override fun sendRecords(records: List<CordaProducerRecord<*, *>>) {
        sendRecordsToPartitions(records.map {
            // Determine the partition
            val topic = it.key
            val numberOfPartitions = topicPartitionMap[topic]
                ?: throw CordaMessageAPIFatalException("Cannot find topic: $topic")
            val partition = getPartition(topic.hashCode(), numberOfPartitions)
            Pair(partition, it)
        })
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        val dbRecords = recordsWithPartitions.map { (partition, record) ->
            val offset = topicService.getLatestOffsets(record.topic)[partition]
                ?: throw CordaMessageAPIFatalException("Cannot find offset for ${record.topic}, partition $partition")

            val serialisedKey = schemaRegistry.serialize(record.key).array()
            val serialisedValue = if (record.value != null) {
                schemaRegistry.serialize(record.value!!).array()
            } else {
                null
            }
            RecordDbEntry(
                record.topic,
                partition,
                offset,
                serialisedKey,
                serialisedValue,
            )
        }

        doSendRecordsToTopicAndDB(dbRecords, recordsWithPartitions)
    }

    private fun doSendRecordsToTopicAndDB(
        dbRecords: List<RecordDbEntry>,
        recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>
    ) {
        // First try adding to DB as it has the possibility of failing
        dbWriterImpl.writeRecords(dbRecords, immediatelyVisible = !inTransaction)
        // Topic service shouldn't fail but if it does the DB will still rollback from here
        recordsWithPartitions.forEach {
            topicService.addRecordsToPartition(listOf(it.second.toCordaRecord()), it.first)
        }
    }

    override fun beginTransaction() {
        if (inTransaction) {
            throw CordaMessageAPIFatalException("Cannot start a new transaction when one is already in progress.")
        }
    }

    override fun sendRecordOffsetsToTransaction(
        consumer: CordaConsumer<*, *>,
        records: List<CordaConsumerRecord<*, *>>
    ) {
        if (!inTransaction) {
            throw CordaMessageAPIFatalException("No transaction is available for sending record offsets.")
        }

        records
            .groupBy { it.topic }
            .forEach { (topic, recordList) ->

                val offsetsPerPartition = recordList
                    .groupBy { it.partition }
                    .mapValues { it.value.maxOf { record -> record.offset } }

                dbWriterImpl.writeOffsets(
                    topic,
                    consumer.toString(), // TODO: Need the ConsumerGroup!!!
                    offsetsPerPartition,
                )
            }
    }

    override fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>) {
        val topicPartitions = consumer.assignment()
        topicPartitions.forEach { (topic, partition) ->
            val offset = topicService.getLatestOffsets(topic)[partition]
                ?: throw CordaMessageAPIFatalException("Cannot find offset for (topic, partition): ($topic, $partition)")

            dbWriterImpl.writeOffsets(
                topic,
                consumer.toString(), // TODO: Need the ConsumerGroup!!!
                mapOf(partition to offset),
            )
        }
    }

    override fun commitTransaction() {
        if (!inTransaction) {
            throw CordaMessageAPIFatalException("No transaction is available to commit.")
        }
        dbWriterImpl.commitRecords(transactionalRecords.get())
        transactionalRecords.get().clear()
    }

    override fun abortTransaction() {
        if (!inTransaction) {
            throw CordaMessageAPIFatalException("No transaction is available to abort.")
        }
        transactionalRecords.get().clear()
    }

    override fun close(timeout: Duration) {
        if (inTransaction) {
            log.error("Close called during transaction.  Some data may have been lost.")
            transactionalRecords.get().clear()
        }
    }

    override fun close() = close(defaultTimeout)

    private fun getPartition(key: Any, numberOfPartitions: Int): Int {
        require(numberOfPartitions > 0)
        return abs(key.hashCode() % numberOfPartitions) + 1
    }
}
