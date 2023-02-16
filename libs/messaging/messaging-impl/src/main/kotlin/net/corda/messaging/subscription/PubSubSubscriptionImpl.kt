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
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.listener.PubSubConsumerRebalanceListener
import net.corda.messaging.utils.toRecord
import net.corda.metrics.CordaMetrics
import net.corda.v5.base.types.ByteArrays.toHexString
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory

/**
 * Corda implementation of a PubSubSubscription.
 * Subscription will continuously try to connect to the message bus based on the [subscriptionConfig] and [config].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [executor] if it is not null. Otherwise they are processed on the same thread.
 * [executor] will be shutdown when the subscription is stopped.
 * @property config kafka configuration
 * @property cordaConsumerBuilder builder to generate a kafka consumer.
 * @property processor processes records from kafka topic. Does not produce any outputs.
 * @property executor if not null, processor is executed using the executor synchronously.
 *                    If executor is null processor executed on the same thread as the consumer.
 *
 */
internal class PubSubSubscriptionImpl<K : Any, V : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val processor: PubSubProcessor<K, V>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private var threadLooper =
        ThreadLooper(log, config, lifecycleCoordinatorFactory, "pubsub processing thread", ::runConsumeLoop)

    private val errorMsg = "PubSubConsumer failed to create and subscribe consumer for group ${config.group}, " +
            "topic ${config.topic}."

    private val processorMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, "PubSub")
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, "onNext")
        .build()

    override val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting subscription with config:\n$config" }
        threadLooper.start()
    }

    override fun close() = threadLooper.close()

    /**
     * Create a Consumer for the given [subscriptionConfig] and [config] and subscribe to the topic.
     * Attempt to create this connection until it is successful while subscription is active.
     * After connection is made begin to process records indefinitely. Mark each record and committed after processing.
     * If an error occurs while processing reset the consumers position on the topic to the last committed position.
     * Execute the processor using the given [executor] if it is not null, otherwise execute on the current thread.
     * If subscription is stopped close the consumer.
     * @throws CordaMessageAPIFatalException if unrecoverable error occurs
     */
    private fun runConsumeLoop() {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            attempts++
            try {
                val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.PUBSUB)
                cordaConsumerBuilder.createConsumer(
                    consumerConfig,
                    config.messageBusConfig,
                    processor.keyClass,
                    processor.valueClass,
                    ::logFailedDeserialize
                ).use {
                    val listener = PubSubConsumerRebalanceListener(
                        config.topic, config.group, it
                    )
                    it.setDefaultRebalanceListener(listener)
                    it.subscribe(config.topic)
                    threadLooper.updateLifecycleStatus(LifecycleStatus.UP)
                    pollAndProcessRecords(it)
                }
                attempts = 0
            } catch (ex: CordaMessageAPIIntermittentException) {
                log.warn(
                    "$errorMsg Attempts: $attempts. Retrying.", ex
                )
            } catch (ex: CordaMessageAPIFatalException) {
                log.error(
                    "$errorMsg Fatal error occurred. Closing subscription.", ex
                )
                threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                threadLooper.stopLoop()
            } catch (ex: Exception) {
                log.error(
                    "$errorMsg Attempts: $attempts. Unexpected error occurred. Closing subscription.", ex
                )
                threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                threadLooper.stopLoop()
            }
        }
    }

    /**
     * Poll records with the [consumer] and process them with the [processor].
     * If an exception is thrown while polling, try to poll again. If this continues to fail break out of the loop.
     * This will recreate the consumer to fetch at the latest position and poll again.
     * @throws CordaMessageAPIIntermittentException if the records cannot be polled at the current position or cannot be processed and max
     * retries have been exceeded.
     */
    private fun pollAndProcessRecords(consumer: CordaConsumer<K, V>) {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            try {
                val consumerRecords = consumer.poll(config.pollTimeout)
                processPubSubRecords(consumerRecords)
                attempts = 0
            } catch (ex: Exception) {
                attempts++
                if (attempts <= config.processorRetries) {
                    log.warn(
                        "PubSubConsumer from group ${config.group} failed to read records from topic ${config.topic}." +
                            " Attempts: $attempts.")
                } else {
                    val message =
                        "PubSubConsumer from group ${config.group} failed to read records from topic ${config.topic}." +
                            "Max retries for poll and process exceeded. Recreating consumer."
                    log.warn(message, ex)
                    throw CordaMessageAPIIntermittentException(message, ex)
                }
            }
        }
    }

    /**
     * Process [cordaConsumerRecords]. If a record fails to deserialize skip this record and log the error.
     * If an exception is thrown when processing a record then this is logged, and we move on to the next record.
     */
    private fun processPubSubRecords(cordaConsumerRecords: List<CordaConsumerRecord<K, V>>) {
        val futures = cordaConsumerRecords.mapNotNull {
            try {
                processorMeter.recordCallable { processor.onNext(it.toRecord()) }
            } catch (except: Exception) {
                log.warn("PubSubConsumer from group ${config.group} failed to process records from topic ${config.topic}.", except)
                null
            }
        }
        futures.forEach {
            try {
                it.get()
            } catch (except: Exception) {
                log.warn("PubSubConsumer from group ${config.group} failed to process records from topic ${config.topic}.", except)
            }
        }
    }

    private fun logFailedDeserialize(data: ByteArray) {
        log.error("Failed to deserialize a record on ${config.topic}: (${toHexString(data)}")
    }
}
