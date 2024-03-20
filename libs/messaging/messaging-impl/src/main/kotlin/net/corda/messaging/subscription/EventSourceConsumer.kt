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
    group: String,
    topic: String,
    private val eventSourceCordaConsumerFactory: EventSourceCordaConsumerFactory<K, V>,
    private val eventSourceRecordConsumer: EventSourceRecordConsumer<K, V>,
    private val log: Logger
) {

    private val errorMsg =
        "Failed to read and process records from topic=${topic}, group=${group}. Recreating consumer and Retrying."

    private var cordaConsumer: CordaConsumer<K, V>? = null

    /**
     * Polls the event source consumer.
     */
    fun poll() {
        if (cordaConsumer == null) {
            cordaConsumer = eventSourceCordaConsumerFactory.create()
        }

        val consumer = checkNotNull(cordaConsumer)

        try {
            eventSourceRecordConsumer.poll(consumer)
        } catch (ex: Exception) {
            when (ex) {
                // If we receive an intermittent exception we dispose the existing
                // consumer and then let the poller call again to recreate a new one
                is CordaMessageAPIIntermittentException -> {
                    log.warn("$errorMsg ", ex)
                    disposeConsumer()
                }

                // Anything other than an intermittent exception should be thrown to the poller
                else -> {
                    disposeConsumer()
                    throw CordaMessageAPIFatalException(
                        "Unrecoverable event source subscription exception, the subscription will be closed.",
                        ex
                    )
                }
            }
        }
    }

    private fun disposeConsumer() {
        cordaConsumer?.close()
        cordaConsumer = null
    }
}