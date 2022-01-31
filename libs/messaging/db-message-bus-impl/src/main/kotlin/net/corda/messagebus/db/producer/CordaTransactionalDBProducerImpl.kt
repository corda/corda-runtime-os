package net.corda.messagebus.db.producer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.persistence.CommittedOffsetEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.TopicRecordEntry
import net.corda.messagebus.db.persistence.TransactionRecordEntry
import net.corda.messagebus.db.toCordaRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.schema.registry.AvroSchemaRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class CordaTransactionalDBProducerImpl(
    private val schemaRegistry: AvroSchemaRegistry,
    private val topicService: TopicService,
    private val dbAccessImpl: DBAccess
) : CordaProducer {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val defaultTimeout: Duration = Duration.ofSeconds(1)
    private val topicPartitionMap = dbAccessImpl.getTopicPartitionMap()

    private val transactionalRecords = ThreadLocal.withInitial { mutableListOf<TopicRecordEntry>() }
    private val transaction = AtomicReference<TransactionRecordEntry?>()
    private val transactionId: String
        get() = transaction.get()?.transaction_id ?: throw CordaMessageAPIFatalException("Bug in producer!")
    private val inTransaction: Boolean
        get() = transaction.get() != null

    override fun send(record: CordaProducerRecord<*, *>, callback: CordaProducer.Callback?) {
        verifyInTransaction()
        sendRecords(listOf(record))
        callback?.onCompletion(null)
    }

    override fun send(record: CordaProducerRecord<*, *>, partition: Int, callback: CordaProducer.Callback?) {
        verifyInTransaction()
        sendRecordsToPartitions(listOf(Pair(partition, record)))
        callback?.onCompletion(null)
    }

    override fun sendRecords(records: List<CordaProducerRecord<*, *>>) {
        verifyInTransaction()
        sendRecordsToPartitions(records.map {
            // Determine the partition
            val topic = it.topic
            val numberOfPartitions = topicPartitionMap[topic]
                ?: throw CordaMessageAPIFatalException("Cannot find topic: $topic")
            val partition = getPartition(topic.hashCode(), numberOfPartitions)
            Pair(partition, it)
        })
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        verifyInTransaction()
        val dbRecords = recordsWithPartitions.map { (partition, record) ->
            // TODO: Could we move this out and optimize?
            val offset = topicService.getLatestOffsets(record.topic)[partition]
                ?: throw CordaMessageAPIFatalException("Cannot find offset for ${record.topic}, partition $partition")

            val serialisedKey = schemaRegistry.serialize(record.key).array()
            val serialisedValue = if (record.value != null) {
                schemaRegistry.serialize(record.value!!).array()
            } else {
                null
            }
            TopicRecordEntry(
                record.topic,
                partition,
                offset,
                serialisedKey,
                serialisedValue,
                transactionId,
            )
        }

        doSendRecordsToTopicAndDB(dbRecords, recordsWithPartitions)
    }

    private fun doSendRecordsToTopicAndDB(
        dbRecords: List<TopicRecordEntry>,
        recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>
    ) {
        // First try adding to DB as it has the possibility of failing
        dbAccessImpl.writeRecords(dbRecords)
        // Topic service shouldn't fail but if it does the DB will still rollback from here
        recordsWithPartitions.forEach {
            topicService.addRecordsToPartition(listOf(it.second.toCordaRecord()), it.first)
        }
    }

    override fun beginTransaction() {
        if (inTransaction) {
            throw CordaMessageAPIFatalException("Cannot start a new transaction when one is already in progress.")
        }
        val newTransaction = TransactionRecordEntry(UUID.randomUUID().toString(), false)
        transaction.set(newTransaction)
        dbAccessImpl.writeTransactionId(newTransaction)
    }

    override fun sendRecordOffsetsToTransaction(
        consumer: CordaConsumer<*, *>,
        records: List<CordaConsumerRecord<*, *>>
    ) {
        verifyInTransaction()

        records
            .groupBy { it.topic }
            .forEach { (topic, recordList) ->
                val offsets = recordList
                    .groupBy { it.partition }
                    .mapValues { it.value.maxOf { record -> record.offset } }
                    .map { (partition, offset) ->
                        CommittedOffsetEntry(
                            topic,
                            consumer.toString(), // TODO: Need the ConsumerGroup!!!
                            partition,
                            offset
                        )
                    }

                dbAccessImpl.writeOffsets(offsets)
            }
    }

    override fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>) {
        verifyInTransaction()
        val topicPartitions = consumer.assignment()
        val offsets = topicPartitions.map { (topic, partition) ->
            // TODO: Could we move this out and optimize?
            val offset = topicService.getLatestOffsets(topic)[partition]
                ?: throw CordaMessageAPIFatalException("Cannot find offset for (topic, partition): ($topic, $partition)")
            CommittedOffsetEntry(
                topic,
                consumer.toString(), // TODO: Need the ConsumerGroup!!!
                partition,
                offset
            )
        }
        dbAccessImpl.writeOffsets(offsets)
    }

    override fun commitTransaction() {
        verifyInTransaction()
        dbAccessImpl.makeRecordsVisible(transactionId)
        transactionalRecords.get().clear()
        transaction.set(null)
    }

    override fun abortTransaction() {
        verifyInTransaction()
        transactionalRecords.get().clear()
    }

    override fun close(timeout: Duration) {
        if (inTransaction) {
            log.error("Close called during transaction.  Some data may have been lost.")
            transactionalRecords.get().clear()
        }
    }

    override fun close() = close(defaultTimeout)

    private fun verifyInTransaction() {
        if (!inTransaction) {
            throw CordaMessageAPIFatalException("No transaction is available for the command.")
        }
    }

    private fun getPartition(key: Any, numberOfPartitions: Int): Int {
        require(numberOfPartitions > 0)
        return abs(key.hashCode() % numberOfPartitions) + 1
    }
}
