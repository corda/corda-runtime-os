package net.corda.messagebus.db.producer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.consumer.DBCordaConsumerImpl
import net.corda.messagebus.db.datamodel.CommittedOffsetEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.util.LatestOffsets
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.schema.registry.AvroSchemaRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.math.abs

class CordaTransactionalDBProducerImpl(
    private val schemaRegistry: AvroSchemaRegistry,
    private val dbAccess: DBAccess
) : CordaProducer {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val defaultTimeout: Duration = Duration.ofSeconds(1)
    private val topicPartitionMap = dbAccess.getTopicPartitionMap()
    private val latestOffsets = LatestOffsets(dbAccess.getMaxOffsetsPerTopicPartition())

    private val transaction = ThreadLocal<TransactionRecordEntry>()
    private val transactionId: String
        get() = transaction.get()?.transactionId
            ?: throw CordaMessageAPIFatalException("Transaction Id must be created before this point")
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
            val partition = getPartition(it.key, numberOfPartitions)
            Pair(partition, it)
        })
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        verifyInTransaction()
        val dbRecords = recordsWithPartitions.map { (partition, record) ->
            val offset = latestOffsets.getNextOffsetFor(CordaTopicPartition(record.topic, partition))

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

        doSendRecordsToTopicAndDB(dbRecords)
    }

    private fun doSendRecordsToTopicAndDB(
        dbRecords: List<TopicRecordEntry>
    ) {
        // First try adding to DB as it has the possibility of failing
        dbAccess.writeRecords(dbRecords)
    }

    override fun beginTransaction() {
        if (inTransaction) {
            throw CordaMessageAPIFatalException("Cannot start a new transaction when one is already in progress.")
        }
        val newTransaction = TransactionRecordEntry(UUID.randomUUID().toString())
        transaction.set(newTransaction)
        dbAccess.writeTransactionRecord(newTransaction)
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
                            (consumer as DBCordaConsumerImpl).getConsumerGroup(),
                            partition,
                            offset,
                            transactionId
                        )
                    }

                dbAccess.writeOffsets(offsets)
            }
    }

    override fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>) {
        verifyInTransaction()
        val topicPartitions = consumer.assignment()
        val offsets = topicPartitions.map { (topic, partition) ->
            val offset = latestOffsets.getNextOffsetFor(CordaTopicPartition(topic, partition))
            CommittedOffsetEntry(
                topic,
                (consumer as DBCordaConsumerImpl).getConsumerGroup(),
                partition,
                offset,
                transactionId
            )
        }
        dbAccess.writeOffsets(offsets)
    }

    override fun commitTransaction() {
        verifyInTransaction()
        dbAccess.setTransactionRecordState(transactionId, TransactionState.COMMITTED)
        transaction.set(null)
    }

    override fun abortTransaction() {
        verifyInTransaction()
        dbAccess.setTransactionRecordState(transactionId, TransactionState.ABORTED)
    }

    override fun close(timeout: Duration) {
        if (inTransaction) {
            log.error("Close called during transaction.  Some data may be lost.")
            abortTransaction()
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
        return abs(key.hashCode() % numberOfPartitions)
    }
}
