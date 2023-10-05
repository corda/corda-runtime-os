package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig

/**
 * Factory for creating multi-source event mediator.
 */
interface MultiSourceEventMediatorFactory {

    /**
     * Creates a multi-source event mediator configuration.
     *
     * @param <K> The type of the event key.
     * @param <S> The type of the state.
     * @param <E> The type of the event.
     * @param eventMediatorConfig Multi-source event mediator configuration.
     */
    fun <K : Any, S : Any, E : Any> create(
        eventMediatorConfig: EventMediatorConfig<K, S, E>,
    ): MultiSourceEventMediator<K, S, E>
}
