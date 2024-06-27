package net.corda.messaging.subscription

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.subscription.factory.EventSourceCordaConsumerFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger



/**
 * The [EventSourceConsumer] is responsible for creating and maintaining an instance of a [CordaConsumer]
 * to be used by the [EventSourceRecordConsumer].
 *
 * CordaMessageAPIIntermittentException exceptions cause the consumer to be recreated and polling to continue
 * All other exceptions cause the consumer to be closed and a fatal exception thrown to the caller.
 */
class EventSourceConsumerTest {
    private val topic = "t1"
    private val group = "g1"
    private val consumer1 = mock<CordaConsumer<String, Int>>()
    private val consumer2 = mock<CordaConsumer<String, Int>>()
    private val eventSourceCordaConsumerFactory = mock<EventSourceCordaConsumerFactory<String, Int>>().apply {
        whenever(create()).thenReturn(consumer1, consumer2)
    }
    private val eventSourceRecordConsumer = mock<EventSourceRecordConsumer<String, Int>>()
    private val log = mock<Logger>()
    private val target = EventSourceConsumer(
        group,
        topic,
        eventSourceCordaConsumerFactory,
        eventSourceRecordConsumer,
        log
    )

    /**
     * When poll
     * and is first poll
     * then consumer is created
     * and then the record processor is polled
     * */
    @Test
    fun `first poll creates consumer`() {
        target.poll()
        verify(eventSourceCordaConsumerFactory, times(1)).create()
        verify(eventSourceRecordConsumer, times(1)).poll(consumer1)
    }

    /**
     * When poll
     * and is not first poll
     * then the record processor is polled with existing consumer
     * */
    @Test
    fun `poll uses existing consumer`() {
        target.poll()
        target.poll()
        verify(eventSourceCordaConsumerFactory, times(1)).create()
        verify(eventSourceRecordConsumer, times(2)).poll(consumer1)
    }

    /**
     * When poll
     * and CordaMessageAPIIntermittentException is thrown
     * then close the existing consumer
     * and then create a new consumer on next poll
     * */
    @Test
    fun `poll - intermittent exception logs a waring and recycles consumer`() {
        whenever(eventSourceRecordConsumer.poll(any())).thenThrow(CordaMessageAPIIntermittentException("error"))
        target.poll()
        target.poll()
        verify(eventSourceCordaConsumerFactory, times(2)).create()
        verify(eventSourceRecordConsumer, times(1)).poll(consumer1)
        verify(eventSourceRecordConsumer, times(1)).poll(consumer2)

        verify(consumer1).close()
        verify(log, times(2)).warn(any<String>(), any<Throwable>())
    }

    /**
     * When poll
     * and an unexpected exception is thrown
     * then close the existing consumer
     * and then throw the exception to the caller
     * */
    @Test
    fun `poll - unexpected exception closes consumer and throws fatal exception to caller`() {
        whenever(eventSourceRecordConsumer.poll(any())).thenThrow(IllegalStateException("error"))

        assertThrows<CordaMessageAPIFatalException> {
            target.poll()
        }

        verify(eventSourceCordaConsumerFactory, times(1)).create()
        verify(eventSourceRecordConsumer, times(1)).poll(consumer1)

        verify(consumer1).close()
    }
}