package net.corda.messaging.subscription

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.EventSourceProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.constants.MetricsConstants
import net.corda.metrics.CordaMetrics
import org.slf4j.Logger
import java.time.Duration

/**
 * The [EventSourceRecordConsumer] is responsible for polling and dispatching records to the [EventSourceProcessor].
 * intermittent failures will be retried for a configured maximum number of retries before the poll returns a failure.
 */
@Suppress("LongParameterList")
class EventSourceRecordConsumer<K : Any, V : Any>(
    group: String,
    clientId: String,
    topic: String,
    private val pollTimeout: Duration,
    private val processorRetries: Int,
    private val processor: EventSourceProcessor<K, V>,
    private val convertRecord: (CordaConsumerRecord<K, V>) -> EventLogRecord<K, V>,
    private val logger: Logger
) {
    private val errorMsg =
        "Failed to read and process records from topic ${topic}, group ${group}"

    private val processorMeter = CordaMetrics.Metric.Messaging.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_SOURCE_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.ON_NEXT_OPERATION)
        .build()

    private var attempts = 0

    fun poll(consumer: CordaConsumer<K, V>) {
        try {
            val cordaConsumerRecords = consumer.poll(pollTimeout)

            if (cordaConsumerRecords.isNotEmpty()) {
                processorMeter.recordCallable {
                    processor.onNext(cordaConsumerRecords.map(convertRecord))
                }
            }

            attempts = 0
        } catch (ex: CordaMessageAPIIntermittentException) {
            if (++attempts <= processorRetries) {
                logger.warn("$errorMsg Retrying poll and process. Attempts: $attempts.")
                consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
            } else {
                val message = "$errorMsg Attempts: $attempts. Max reties for poll and process exceeded."
                logger.warn(message, ex)
                // An intermittent exception is thrown here to trigger the event source processor to
                // recreate the kafka consumer and try again.
                throw CordaMessageAPIIntermittentException(message, ex)
            }
        }
    }
}