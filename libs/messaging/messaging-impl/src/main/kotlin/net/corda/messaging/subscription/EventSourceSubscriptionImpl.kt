package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.EventSourceProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.subscription.consumer.listener.ForwardingRebalanceListener
import net.corda.messaging.subscription.consumer.listener.LoggingConsumerRebalanceListener
import net.corda.messaging.utils.ExceptionUtils
import net.corda.messaging.utils.toEventLogRecord
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class EventSourceSubscriptionImpl<K : Any, V : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val processor: EventSourceProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

    private var threadLooper = ThreadLooper(
        log,
        config,
        lifecycleCoordinatorFactory,
        "event source processing thread",
        ::runConsumeLoop
    )

    private val errorMsg =
        "Failed to read and process records from topic ${config.topic}, group ${config.group}"

    private val processorMeter = CordaMetrics.Metric.Messaging.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_SOURCE_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.ON_NEXT_OPERATION)
        .build()

    override val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting subscription with config:\n${config}" }
        threadLooper.start()
    }

    override fun close() = threadLooper.close()

    @Suppress("NestedBlockDepth")
    private fun runConsumeLoop() {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            attempts++
            try {
                log.debug { "Attempt: $attempts" }
                val rebalancedListener = if (partitionAssignmentListener == null) {
                    LoggingConsumerRebalanceListener(config.clientId)
                } else {
                    ForwardingRebalanceListener(config.topic, config.clientId, partitionAssignmentListener)
                }
                val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.EVENT_SOURCE)

                cordaConsumerBuilder.createConsumer(
                    consumerConfig,
                    config.messageBusConfig,
                    processor.keyClass,
                    processor.valueClass,
                    { _ ->
                        log.error("Failed to deserialize record from ${config.topic}")
                    },
                    rebalancedListener
                ).use { cordaConsumer ->
                    cordaConsumer.subscribe(config.topic)
                    threadLooper.updateLifecycleStatus(LifecycleStatus.UP)
                    pollAndProcessRecords(cordaConsumer)
                }

                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn("$errorMsg Attempts: $attempts. Recreating consumer and Retrying.", ex)
                    }

                    else -> {
                        log.error("$errorMsg Attempts: $attempts. Closing subscription.", ex)
                        threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                        threadLooper.stopLoop()
                    }
                }
            }
        }
    }

    private fun pollAndProcessRecords(consumer: CordaConsumer<K, V>) {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            try {
                processRecords(consumer.poll(config.pollTimeout), consumer)
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException -> {
                        throw ex
                    }

                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        handlePollAndProcessIntermittentError(attempts, consumer, ex)
                    }

                    else -> {
                        throw CordaMessageAPIFatalException("$errorMsg Closing consumer", ex)
                    }
                }
            }
        }
    }

    /**
     * Handle whether to log a warning or to throw a [CordaMessageAPIIntermittentException]
     * Throw if max amount of [attempts] have been reached. Otherwisde log warning.
     */
    private fun handlePollAndProcessIntermittentError(
        attempts: Int,
        consumer: CordaConsumer<K, V>,
        ex: Exception
    ) {
        if (attempts <= config.processorRetries) {
            log.warn("$errorMsg Retrying poll and process. Attempts: $attempts.")
            consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
        } else {
            val message = "$errorMsg Attempts: $attempts. Max reties for poll and process exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }

    /**
     * Process Kafka [cordaConsumerRecords]. Commit the [consumer] offset for each record back to the topic after
     * processing them synchronously and writing output records back to kafka in a transaction.
     * If a record fails to deserialize skip this record and log the error.
     * @throws CordaMessageAPIIntermittentException error occurred that can be retried.
     * @throws CordaMessageAPIFatalException Fatal unrecoverable error occurred. e.g misconfiguration
     */
    private fun processRecords(
        cordaConsumerRecords: List<CordaConsumerRecord<K, V>>,
        consumer: CordaConsumer<K, V>
    ) {
        if (cordaConsumerRecords.isEmpty()) {
            return
        }

        try {
            processorMeter.recordCallable {
                processor.onNext(cordaConsumerRecords.map { it.toEventLogRecord() })
            }
        } catch (ex: Exception) {
            when (ex::class.java) {
                in ExceptionUtils.CordaMessageAPIException -> {
                    throw ex
                }

                else -> {
                    throw CordaMessageAPIFatalException("errorMsg Closing consumer", ex)
                }
            }
        }
    }
}
