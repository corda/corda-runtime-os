package net.corda.messagebus.api.consumer;

/**
 * A key/value pair to be received from the message bus. This also consists of a topic name and
 * a partition number from which the record is being received, an offset that points 
 * to the record in a partition, and a timestamp as marked by the corresponding ProducerRecord.
 */
data class CordaConsumerRecord<K, V>(

    /**
     * The topic from which this record is received.
     */
    val topic: String,

    /**
     * The partition from which this record is received.
     */
    val partition: Int,

    /**
     * The position of this record in the corresponding partition.
     */
    val offset: Long,

    /**
     * The key (or null if no key is specified).
     */
    val key: K,

    /**
     * The value (or null if the value should be deleted).
     */
    val value: V?,

    /**
     * The timestamp of this record.
     */
    val timestamp: Long,
)
