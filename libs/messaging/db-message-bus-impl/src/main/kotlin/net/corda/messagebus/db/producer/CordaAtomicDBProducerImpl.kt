package net.corda.messagebus.db.producer

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.DBAccess.Companion.ATOMIC_TRANSACTION
import net.corda.messagebus.db.serialization.MessageHeaderSerializer
import net.corda.messagebus.db.util.WriteOffsets
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import org.slf4j.LoggerFactory
import kotlin.math.abs

@Suppress("TooManyFunctions")
class CordaAtomicDBProducerImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val dbAccess: DBAccess,
    private val writeOffsets: WriteOffsets,
    private val headerSerializer: MessageHeaderSerializer,
    private val throwOnSerializationError: Boolean = true
) : CordaProducer {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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
            val numberOfPartitions = dbAccess.getTopicPartitionMapFor(topic).size
            val partition = getPartition(it.key, numberOfPartitions)
            Pair(partition, it)
        })
    }

    override fun sendRecordsToPartitions(recordsWithPartitions: List<Pair<Int, CordaProducerRecord<*, *>>>) {
        val dbRecords = recordsWithPartitions.mapNotNull { (partition, record) ->
            val offset = writeOffsets.getNextOffsetFor(CordaTopicPartition(record.topic, partition))
            try {
                val serializedKey =
                    wrapWithNullErrorHandling({ CordaMessageAPIFatalException("Failed to serialize key", it) }) {
                        serializer.serialize(record.key)
                    }
                val serializedValue = if (record.value != null) {
                    serializer.serialize(record.value!!)
                } else {
                    null
                }
                val serializedHeaders = headerSerializer.serialize(record.headers)
                TopicRecordEntry(
                    record.topic,
                    partition,
                    offset,
                    serializedKey,
                    serializedValue,
                    serializedHeaders,
                    ATOMIC_TRANSACTION,
                )
            } catch (ex: Exception) {
                val msg = "Failed to send record to topic ${record.topic} with key ${record.key}"
                if (throwOnSerializationError) {
                    logger.error(msg, ex)
                    throw ex
                } else {
                    logger.warn(msg, ex)
                    null
                }
            }
        }

        doSendRecordsToTopicAndDB(dbRecords)
    }

    private fun doSendRecordsToTopicAndDB(
        dbRecords: List<TopicRecordEntry>,
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

    override fun close() {
        dbAccess.close()
    }

    private fun throwNonTransactionalLogic() {
        throw CordaMessageAPIFatalException("Non transactional producer can't do transactional logic.")
    }

    private fun getPartition(key: Any, numberOfPartitions: Int): Int {
        require(numberOfPartitions > 0)
        return abs(key.hashCode() % numberOfPartitions)
    }

    override fun getOffsets(records: List<CordaConsumerRecord<*, *>>): CordaProducer.Offsets {
        TODO("Not yet implemented")
    }

    override fun getMetadata(consumer: CordaConsumer<*, *>): CordaProducer.Metadata {
        TODO("Not yet implemented")
    }

    override fun sendRecordOffsetsToTransaction(offsets: CordaProducer.Offsets, metadata: CordaProducer.Metadata) {
        TODO("Not yet implemented")
    }
}
