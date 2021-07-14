package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.asRecord
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class KafkaRandomAccessSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val consumerBuilder: ConsumerBuilder<K, V>,
): RandomAccessSubscription<K, V> {

    companion object {
        private val log = contextLogger()
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private val topic = config.getString(KafkaProperties.TOPIC_NAME)
    private lateinit var consumer: CordaKafkaConsumer<K, V>
    private var assignedPartitions = emptySet<Int>()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                val configWithOverrides = config.getConfig(KafkaProperties.KAFKA_CONSUMER)
                    .withValue(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, ConfigValueFactory.fromAnyRef(1))
                consumer = consumerBuilder.createDurableConsumer(configWithOverrides)
                val allPartitions = consumer.getPartitions(topic, 5.seconds).map { it.partition() }.toSet()
                consumer.assignPartitionsManually(allPartitions)
                assignedPartitions = allPartitions
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                consumer.close()
                running = false
            }
        }
    }

    @Synchronized
    override fun getRecord(partition: Int, offset: Long): Record<K, V>? {
        checkForPartitionsChange(partition)
        if (partition !in assignedPartitions) {
            return null
        }

        val partitionToBeQueried = TopicPartition(topic, partition)
        val partitionsToBePaused = assignedPartitions
            .filter { it != partitionToBeQueried.partition() }
            .map { TopicPartition(topic, it) }
        consumer.pause(partitionsToBePaused)
        consumer.resume(listOf(partitionToBeQueried))
        consumer.seek(partitionToBeQueried, offset)
        val records = consumer.poll()
            .filter { it.record.partition() == partition && it.record.offset() == offset }

        return when {
            records.isEmpty() -> {
                null
            }
            records.size == 1 -> {
                records.single().asRecord()
            }
            else -> {
                val errorMsg = "Multiple records located for partition=$partition, offset=$offset, topic=$topic : $records."
                log.warn(errorMsg)
                throw CordaMessageAPIFatalException(errorMsg)
            }
        }
    }

    private fun checkForPartitionsChange(partition: Int) {
        // Since we manually assign all partitions, this is only possible if an operator increased the number of partitions of the topic.
        // In this case, we refresh the assignment so there is no need to restart the system and everything keeps working smoothly.
        if (partition !in assignedPartitions) {
            val allPartitions = consumer.getPartitions(topic, 5.seconds).map { it.partition() }.toSet()
            consumer.assignPartitionsManually(allPartitions)
            assignedPartitions = allPartitions
        }
    }

}