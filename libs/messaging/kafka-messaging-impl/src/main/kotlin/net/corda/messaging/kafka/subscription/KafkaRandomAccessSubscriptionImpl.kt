package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.utils.toRecord
import net.corda.v5.base.util.seconds
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class KafkaRandomAccessSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val keyClass: Class<K>,
    private val valueClass: Class<V>,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
): RandomAccessSubscription<K, V> {

    private val log = LoggerFactory.getLogger(
        config.getString(CONSUMER_GROUP_ID)
    )

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()

    private val groupName = config.getString(CONSUMER_GROUP_ID)
    private val topic = config.getString(TOPIC_NAME)
    private var consumer: CordaKafkaConsumer<K, V>? = null
    private var assignedPartitions = emptySet<Int>()

    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "$groupName-KafkaRandomAccessSubscription-$topic",
            config.getString(ConfigProperties.INSTANCE_ID)
        )
    ) { _, _ -> }

    override val isRunning: Boolean
        get() = running

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override fun start() {
        startStopLock.write {
            if (!running) {
                val configWithOverrides = config.getConfig(KAFKA_CONSUMER)
                    .withValue(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, ConfigValueFactory.fromAnyRef(1))
                consumer = consumerBuilder.createDurableConsumer(configWithOverrides, keyClass, valueClass)
                val allPartitions = consumer!!.getPartitions(topic, 5.seconds).map { it.partition() }.toSet()
                consumer!!.assignPartitionsManually(allPartitions)
                assignedPartitions = allPartitions
                running = true
                lifecycleCoordinator.start()
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    override fun stop() {
        stopConsumeLoop()
        lifecycleCoordinator.stop()
    }

    override fun close() {
        stopConsumeLoop()
        lifecycleCoordinator.close()
    }

    private fun stopConsumeLoop() {
        startStopLock.write {
            if (running) {
                consumer!!.close()
                running = false
            }
        }
    }

    @Synchronized
    override fun getRecord(partition: Int, offset: Long): Record<K, V>? {
        startStopLock.read {
            if (running) {
                checkForPartitionsChange(partition)
                if (partition !in assignedPartitions) {
                    return null
                }

                val partitionToBeQueried = TopicPartition(topic, partition)
                val partitionsToBePaused = assignedPartitions
                    .filter { it != partitionToBeQueried.partition() }
                    .map { TopicPartition(topic, it) }
                consumer!!.pause(partitionsToBePaused)
                consumer!!.resume(listOf(partitionToBeQueried))
                consumer!!.seek(partitionToBeQueried, offset)
                val records = consumer!!.poll()
                    .filter { it.partition() == partition && it.offset() == offset }

                return when {
                    records.isEmpty() -> {
                        null
                    }
                    records.size == 1 -> {
                        records.single().toRecord()
                    }
                    else -> {
                        val errorMsg = "Multiple records located for partition=$partition, offset=$offset, topic=$topic : $records."
                        log.warn(errorMsg)
                        throw CordaMessageAPIFatalException(errorMsg)
                    }
                }
            } else {
                throw IllegalStateException("getRecords invoked when subscription was not running.")
            }
        }
    }

    private fun checkForPartitionsChange(partition: Int) {
        // Since we manually assign all partitions, this is only possible if an operator increased the number of partitions of the topic.
        // In this case, we refresh the assignment so there is no need to restart the system and everything keeps working smoothly.
        if (partition !in assignedPartitions) {
            val allPartitions = consumer!!.getPartitions(topic, 5.seconds).map { it.partition() }.toSet()
            consumer!!.assignPartitionsManually(allPartitions)
            assignedPartitions = allPartitions
        }
    }

}