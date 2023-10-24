package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig

/**
 * Factory for creating multi-source event mediator consumers.
 */
interface MediatorConsumerFactory {

    /**
     * Creates a multi-source event mediator consumer.
     *
     * @param <K> The type of the message key.
     * @param <S> The type of the message value (payload).
     * @param config Multi-source event mediator consumer configuration.
     */
    fun <K: Any, V: Any> create(config: MediatorConsumerConfig<K, V>): MediatorConsumer<K, V>
}