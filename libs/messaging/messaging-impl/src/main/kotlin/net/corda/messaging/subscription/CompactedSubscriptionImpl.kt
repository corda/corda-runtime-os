package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.messaging.utils.toRecord
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

internal class CompactedSubscriptionImpl<K : Any, V : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val mapFactory: MapFactory<K, V>,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val processor: CompactedProcessor<K, V>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : CompactedSubscription<K, V> {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val errorMsg = "Failed to read records from group ${config.group}, topic ${config.topic}"

    private var threadLooper =
        ThreadLooper(log, config, lifecycleCoordinatorFactory, "compacted subscription thread", ::runConsumeLoop)

    private var latestValues: MutableMap<K, V>? = null

    private val processorMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, "Compacted")
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, "onNext")
        .build()

    private val snapshotMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, "Compacted")
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, "onSnapshot")
        .build()

    override fun close() = threadLooper.close()

    override fun start() {
        log.debug { "Starting subscription with config:\n${config}" }
        threadLooper.start()
    }

    override val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun getValue(key: K): V? = latestValues?.get(key)

    private fun runConsumeLoop() {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            attempts++
            try {
                log.debug { "Creating compacted consumer.  Attempt: $attempts" }
                val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.COMPACTED)
                cordaConsumerBuilder.createConsumer(
                    consumerConfig,
                    config.messageBusConfig,
                    processor.keyClass,
                    processor.valueClass,
                    ::onError
                ).use {
                    val partitions = it.getPartitions(
                        config.topic
                    )
                    it.assign(partitions)
                    pollAndProcessSnapshot(it)
                    threadLooper.updateLifecycleStatus(LifecycleStatus.UP)
                    pollAndProcessRecords(it)
                }
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn("$errorMsg. Attempts: $attempts. Retrying.", ex)
                    }

                    else -> {
                        log.error("$errorMsg. Fatal error occurred. Closing subscription.", ex)
                        threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                        threadLooper.stopLoop()
                    }
                }
            }
        }
        latestValues?.apply { mapFactory.destroyMap(this) }
        latestValues = null
    }

    private fun onError(bytes: ByteArray) {
        log.error("Failed to deserialize record from ${config.topic} with bytes $bytes")
    }

    private fun getLatestValues(): MutableMap<K, V> {
        var latest = latestValues
        if (latest == null) {
            latest = mapFactory.createMap()
            latestValues = latest
        }
        return latest
    }

    private fun pollAndProcessSnapshot(consumer: CordaConsumer<K, V>) {
        val partitions = consumer.assignment()
        val endOffsets = consumer.endOffsets(partitions)
        val snapshotEnds = endOffsets.toMutableMap()
        consumer.seekToBeginning(partitions)

        val currentData = getLatestValues()
        currentData.clear()

        while (snapshotEnds.isNotEmpty()) {
            val consumerRecords = consumer.poll(config.pollTimeout)

            consumerRecords.forEach {
                val value = it.value
                if (value != null) {
                    currentData[it.key] = value
                } else {
                    currentData.remove(it.key)
                }
            }

            for (offsets in endOffsets) {
                val partition = offsets.key
                if (consumer.position(partition) >= offsets.value) {
                    snapshotEnds.remove(partition)
                }
            }
        }

        snapshotMeter.recordCallable { processor.onSnapshot(currentData) }
    }

    private fun pollAndProcessRecords(consumer: CordaConsumer<K, V>) {
        while (!threadLooper.loopStopped) {
            val consumerRecords = consumer.poll(config.pollTimeout)
            try {
                processCompactedRecords(consumerRecords)
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException,
                    is CordaMessageAPIIntermittentException -> {
                        throw ex
                    }

                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic ${config.topic}, group ${config.group}.", ex
                        )
                    }
                }
            }
        }
    }

    private fun processCompactedRecords(
        cordaConsumerRecords: List<CordaConsumerRecord<K, V>>
    ) {
        val currentData = getLatestValues()
        cordaConsumerRecords.forEach {
            val oldValue = currentData[it.key]
            val newValue = it.value

            if (newValue == null) {
                currentData.remove(it.key)
            } else {
                currentData[it.key] = newValue
            }

            processorMeter.recordCallable { processor.onNext(it.toRecord(), oldValue, currentData) }
        }
    }
}
