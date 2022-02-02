package net.corda.messagebus.db.producer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.conversions.toCordaRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.schema.registry.AvroSchemaRegistry
import java.time.Duration
import kotlin.math.abs

class CordaAtomicDBProducerImpl(
    private val schemaRegistry: AvroSchemaRegistry,
    private val topicService: TopicService,
    private val dbAccess: DBAccess
) : CordaProducer {

    companion object {
        internal val ATOMIC_TRANSACTION = TransactionRecordEntry("Atomic Transaction", true)
    }

    private val defaultTimeout: Duration = Duration.ofSeconds(1)
    private val topicPartitionMap = dbAccess.getTopicPartitionMap()

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
            val partition = getPartition(topic.hashCode(), numberOfPartitions)
            Pair(partition, it)
        })
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
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
                ATOMIC_TRANSACTION.transaction_id,
            )
        }

        doSendRecordsToTopicAndDB(dbRecords, recordsWithPartitions)
    }

    private fun doSendRecordsToTopicAndDB(
        dbRecords: List<TopicRecordEntry>,
        recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>
    ) {
        // First try adding to DB as it has the possibility of failing.
        dbAccess.writeTransactionId(ATOMIC_TRANSACTION)
        dbAccess.writeRecords(dbRecords)
        dbAccess.makeRecordsVisible(ATOMIC_TRANSACTION.transaction_id)
        // Topic service shouldn't fail but if it does the DB will still rollback from here
        recordsWithPartitions.forEach {
            topicService.addRecordsToPartition(listOf(it.second.toCordaRecord()), it.first)
        }
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

    private fun throwNonTransactionalLogic() {
        throw CordaMessageAPIFatalException("Non transactional producer can't do transactional logic.")
    }

    override fun close(timeout: Duration) {
    }

    override fun close() = close(defaultTimeout)

    private fun getPartition(key: Any, numberOfPartitions: Int): Int {
        require(numberOfPartitions > 0)
        return abs(key.hashCode() % numberOfPartitions) + 1
    }

}
