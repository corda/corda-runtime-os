package net.corda.messaging.mediator.slim

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.mediator.MediatorSubscriptionState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class SlimConsumerProcessor<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val mediatorSubscriptionState: MediatorSubscriptionState,
    private val messageBusConsumerFacFactory: SlimMessageBusConsumerFacFactory,
    private val slimEventProcessor: SlimEventProcessor<K, E>,
) {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun processTopic(consumerConfig: MediatorConsumerConfig<K, E>) {
        var attempts = 0
        var consumer: SlimMessageBusConsumer<K, E>? = null
        while (!mediatorSubscriptionState.stopped()) {
            if (consumer == null) {
                consumer = messageBusConsumerFacFactory.create(consumerConfig)
                consumer.subscribe()
            }
            slimEventProcessor.enqueueEvents(consumer.poll(config.pollTimeout))
        }
    }
}
