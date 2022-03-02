package net.corda.messagebus.db.producer

import net.corda.data.CordaAvroSerializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.util.WriteOffsets
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.time.Duration
import javax.persistence.RollbackException
import kotlin.math.abs

@Suppress("TooManyFunctions")
class CordaAtomicDBProducerImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val dbAccess: DBAccess
) : CordaProducer {

    companion object {
        val log = contextLogger()

        val ATOMIC_TRANSACTION = TransactionRecordEntry("Atomic Transaction", TransactionState.COMMITTED)
    }

    private fun initialiseWithAtomicTransaction() {
        try {
            // Write the transaction record for all atomic transactions
            dbAccess.writeTransactionRecord(ATOMIC_TRANSACTION)
        } catch (e: RollbackException) {
            log.debug { "ATOMIC_TRANSACTION already recorded in DB." }
            // It's already been written so do nothing
        }
    }

    init {
        initialiseWithAtomicTransaction()
    }

    private val defaultTimeout: Duration = Duration.ofSeconds(1)
    private val topicPartitionMap = dbAccess.getTopicPartitionMap()
    private val writeOffsets = WriteOffsets(dbAccess.getMaxOffsetsPerTopicPartition())

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
            val topic = it.topic
            val numberOfPartitions = topicPartitionMap[topic]
                ?: throw CordaMessageAPIFatalException("Cannot find topic: $topic")
            val partition = getPartition(it.key, numberOfPartitions)
            Pair(partition, it)
        })
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        val dbRecords = recordsWithPartitions.map { (partition, record) ->
            val offset = writeOffsets.getNextOffsetFor(CordaTopicPartition(record.topic, partition))
            val serializedKey = serializer.serialize(record.key)
                ?: throw CordaMessageAPIFatalException("Serialized Key cannot be null")
            val serializedValue = if (record.value != null) {
                serializer.serialize(record.value!!)
            } else {
                null
            }
            TopicRecordEntry(
                record.topic,
                partition,
                offset,
                serializedKey,
                serializedValue,
                ATOMIC_TRANSACTION,
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
        throwNonTransactionalLogic()
    }

    override fun sendRecordOffsetsToTransaction(
        consumer: CordaConsumer<*, *>,
        records: List<CordaConsumerRecord<*, *>>
    ) {
        throwNonTransactionalLogic()
    }

    override fun sendAllOffsetsToTransaction(consumer: CordaConsumer<*, *>) {
        throwNonTransactionalLogic()
    }

    override fun commitTransaction() {
        throwNonTransactionalLogic()
    }

    override fun abortTransaction() {
        throwNonTransactionalLogic()
    }

    override fun close(timeout: Duration) {
    }

    override fun close() = close(defaultTimeout)

    private fun throwNonTransactionalLogic() {
        throw CordaMessageAPIFatalException("Non transactional producer can't do transactional logic.")
    }

    private fun getPartition(key: Any, numberOfPartitions: Int): Int {
        require(numberOfPartitions > 0)
        return abs(key.hashCode() % numberOfPartitions)
    }

}
