package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MediatorProducer
import net.corda.messaging.api.mediator.config.MediatorProducerConfig

/**
 * Factory for creating multi-source event mediator producers.
 */
interface MediatorProducerFactory {

    /**
     * Creates a multi-source event mediator producer.
     *
     * @param config Multi-source event mediator producer configuration.
     */
    fun create(config: MediatorProducerConfig): MediatorProducer
}