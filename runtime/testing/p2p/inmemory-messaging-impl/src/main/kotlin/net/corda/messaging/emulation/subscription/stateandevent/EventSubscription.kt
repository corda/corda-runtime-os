package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata

internal class EventSubscription<K : Any, S : Any, E : Any>(
    internal val subscription: InMemoryStateAndEventSubscription<K, S, E>,
) : Lifecycle {

    private var eventsConsumption: Consumption? = null
    override val isRunning: Boolean
        get() =
            eventsConsumption?.isRunning ?: false

    override fun start() {
        if (eventsConsumption == null) {
            val consumer = EventConsumer(this)
            eventsConsumption = subscription.topicService.createConsumption(consumer)
        }
    }

    override fun stop() {
        eventsConsumption?.stop()
        eventsConsumption = null
    }

    internal fun processEvents(records: Collection<RecordMetadata>) {
        subscription.stateSubscription.waitForReady()
        records.forEach { eventMetaData ->
            val event = eventMetaData.castToType(
                subscription.processor.keyClass,
                subscription.processor.eventValueClass
            )
            if (event != null) {
                val state = subscription.stateSubscription.getValue(event.key)
                val response = subscription.processor.onNext(state, event)
                subscription.setValue(event.key, response.updatedState, eventMetaData.partition)
                subscription.topicService.addRecords(
                    listOf(
                        Record(
                            subscription.stateSubscriptionConfig.eventTopic,
                            event.key,
                            response.updatedState
                        )
                    ) +
                        response.responseEvents
                )
                subscription.stateAndEventListener?.onPostCommit(mapOf(event.key to response.updatedState))
            }
        }
    }
}
