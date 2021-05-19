package net.corda.libs.kafka.topic.utils

import org.apache.kafka.clients.producer.KafkaProducer
import java.util.*


/**
 * Kafka utility for topic administration
 */
interface TopicUtils {

    /**
     * Create new topic based on:
     * [topicName]
     * [partitions]
     * [replication]
     * [kafkaProps]
     */
    fun createTopic(topicName: String,
                    partitions: Int,
                    replication: Short,
                    kafkaProps: Properties
    )
    //remove when write lib is done
    fun createProducer(props: Properties, keySerialiser: String?, valueSerialiser: String?) : KafkaProducer<Any, Any>

}