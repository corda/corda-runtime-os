package net.corda.messaging.subscription

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.subscription.factory.EventSourceCordaConsumerFactory
import org.slf4j.Logger

/**
 * The [EventSourceConsumer] is responsible for reliably creating a corda consumer and passing
 * it to an [EventSourceRecordConsumer] to use.
 */
internal class EventSourceConsumer<K : Any, V : Any>(
    private val group: String,
    private val topic: String,
    private val eventSourceCordaConsumerFactory: EventSourceCordaConsumerFactory<K, V>,
    private val eventSourceRecordConsumer: EventSourceRecordConsumer<K, V>,
    private val log: Logger
) {
    private var cordaConsumer: CordaConsumer<K, V>? = null

    /**
     * Polls the event source consumer.
     */
    fun poll() {
        val consumer = cordaConsumer.let {
            it ?: eventSourceCordaConsumerFactory.create().also {
                cordaConsumer = it
            }
        }

        try {
            eventSourceRecordConsumer.poll(consumer)
        } catch (ex: CordaMessageAPIIntermittentException) {
            log.warn(
                "Failed to read and process records from topic=$topic, group=$group. Recreating consumer and Retrying.",
                ex
            )
            disposeConsumer()
        } catch (ex: Exception) {
            disposeConsumer()
            throw CordaMessageAPIFatalException(
                "Unrecoverable event source subscription exception, the subscription will be closed.",
                ex
            )
        }
    }
    
    private fun disposeConsumer() {
        cordaConsumer?.close()
        cordaConsumer = null
    }
}