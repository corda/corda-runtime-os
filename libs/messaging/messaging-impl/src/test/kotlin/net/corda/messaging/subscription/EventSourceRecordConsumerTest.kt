package net.corda.messaging.subscription

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.EventSourceProcessor
import net.corda.messaging.api.records.EventLogRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import java.time.Duration

/**
 * The [EventSourceRecordConsumer] is responsible for polling and dispatching records to the [EventSourceProcessor].
 * intermittent failures will be retried for a configured maximum number of retries before the poll returns a failure.
 */
class EventSourceRecordConsumerTest {
    private val topic = "t1"
    private val clientId = "c1"
    private val group = "g1"
    private val pollTimeout = Duration.ofSeconds(1)
    private val processorRetries = 2
    private val processor = Mockito.mock<EventSourceProcessor<String, Int>>()
    private val log = Mockito.mock<Logger>()
    private val cordaConsumer = Mockito.mock<CordaConsumer<String, Int>>()
    private val recordsConverted = mutableListOf<CordaConsumerRecord<String, Int>>()
    private val cordaRecord = CordaConsumerRecord("", 0, 0, "", 0, 0)
    private val convertedRecord = EventLogRecord("", "", 0, 0, 0, 0)
    private val target = EventSourceRecordConsumer(
        topic,
        clientId,
        group,
        pollTimeout,
        processorRetries,
        processor,
        { record ->
            recordsConverted.add(record)
            convertedRecord
        },
        log
    )

    /**
     * Given a poll
     * and the consumer returns nothing
     * then do nothing
     */
    @Test
    fun `poll - empty records does nothing`() {
        whenever(cordaConsumer.poll(pollTimeout)).thenReturn(listOf())
        target.poll(cordaConsumer)
        verify(processor, Mockito.times(0)).onNext(any())
    }

    /**
     * Given a poll
     * and the consumer returns a list of records
     * then convert the records
     * and call the record processor
     */
    @Test
    fun `poll - records converted and sent to processor`() {
        whenever(cordaConsumer.poll(pollTimeout)).thenReturn(listOf(cordaRecord))
        target.poll(cordaConsumer)
        verify(processor, Mockito.times(1)).onNext(listOf(convertedRecord))
    }

    /**
     * Given a poll
     * and a CordaMessageAPIIntermittentException exception is caught
     * then reset the consumer offsets to earliest
     * and log a warning
     */
    @Test
    fun `poll - intermittent exception resets offsets on consumer and logs a warning`() {
        whenever(cordaConsumer.poll(any())).thenThrow(CordaMessageAPIIntermittentException(""))
        target.poll(cordaConsumer)
        verify(processor, Mockito.times(0)).onNext(any())
        verify(cordaConsumer).resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
        verify(log).warn(any())
    }

    /**
     * Given a poll
     * and a CordaMessageAPIIntermittentException exception is caught
     * and the intermittent exception has persisted for more polls than the configured maximum
     * then throw an CordaMessageAPIIntermittentException to the caller
     * and log a warning
     */
    @Test
    fun `poll - intermittent exception throws to caller after max attempts`() {
        whenever(cordaConsumer.poll(any())).thenThrow(CordaMessageAPIIntermittentException(""))
        target.poll(cordaConsumer)
        target.poll(cordaConsumer)
        assertThrows<CordaMessageAPIIntermittentException> {
            target.poll(cordaConsumer)
        }
        // Warn twice for the retries
        verify(log,times(2)).warn(any())
        // Warn for the raised error when retying fails
        verify(log).warn(any<String>(),any<Throwable>())
    }
}