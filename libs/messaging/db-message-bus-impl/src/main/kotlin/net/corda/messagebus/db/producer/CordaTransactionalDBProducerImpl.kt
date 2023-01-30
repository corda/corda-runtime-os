package net.corda.messagebus.db.producer

import net.corda.data.CordaAvroSerializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.consumer.DBCordaConsumerImpl
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.util.WriteOffsets
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.abs

class CordaTransactionalDBProducerImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val dbAccess: DBAccess,
    private val writeOffsets: WriteOffsets
) : CordaProducer {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val _transaction = ThreadLocal<TransactionRecordEntry>()
    private var transaction
        get() = _transaction.get()
        set(value) = _transaction.set(value)
    private val transactionId: String
        get() = transaction.transactionId
    private val inTransaction: Boolean
        get() = transaction != null

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
            val numberOfPartitions = dbAccess.getTopicPartitionMapFor(topic).size
            val partition = getPartition(it.key, numberOfPartitions)
            Pair(partition, it)
        })
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        verifyInTransaction()
        val dbRecords = recordsWithPartitions.map { (partition, record) ->
            val offset = writeOffsets.getNextOffsetFor(CordaTopicPartition(record.topic, partition))

            val serialisedKey = serializer.serialize(record.key)
                ?: throw CordaMessageAPIFatalException("Serialized Key cannot be null")
            val serialisedValue = if (record.value != null) {
                serializer.serialize(record.value!!)
            } else {
                null
            }
            TopicRecordEntry(
                record.topic,
                partition,
                offset,
                serialisedKey,
                serialisedValue,
                transaction,
            )
        }

        doSendRecordsToTopicAndDB(dbRecords)
    }

    private fun doSendRecordsToTopicAndDB(
        dbRecords: List<TopicRecordEntry>
    ) {
        dbAccess.writeRecords(dbRecords)
    }

    override fun beginTransaction() {
        if (inTransaction) {
            throw CordaMessageAPIFatalException("Cannot start a new transaction when one is already in progress.")
        }
        val newTransaction = TransactionRecordEntry(UUID.randomUUID().toString())
        transaction = newTransaction
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
                        CommittedPositionEntry(
                            topic,
                            (consumer as DBCordaConsumerImpl).getConsumerGroup(),
                            partition,
                            offset,
                            transaction
                        )
                    }

                dbAccess.writeOffsets(offsets)
            }
    }

    override fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>) {
        verifyInTransaction()
        val topicPartitions = consumer.assignment()
        val offsetsPerPartition = topicPartitions.associateWith { consumer.position(it) }
        val offsets = offsetsPerPartition.map { (topicPartition, offset) ->
            CommittedPositionEntry(
                topicPartition.topic,
                (consumer as DBCordaConsumerImpl).getConsumerGroup(),
                topicPartition.partition,
                offset,
                transaction
            )
        }
        dbAccess.writeOffsets(offsets)
    }

    override fun commitTransaction() {
        verifyInTransaction()
        dbAccess.setTransactionRecordState(transactionId, TransactionState.COMMITTED)
        transaction = null
    }

    override fun abortTransaction() {
        verifyInTransaction()
        dbAccess.setTransactionRecordState(transactionId, TransactionState.ABORTED)
        transaction = null
    }

    override fun close() {
        if (inTransaction) {
            log.warn("Close called during transaction.  Some data may be lost.")
            abortTransaction()
        }
        dbAccess.close()
    }

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
