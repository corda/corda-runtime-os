package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MediatorProducer

/**
 * Mediator producer finder is used to access [MediatorProducer] by its ID.
 */
fun interface MediatorProducerFinder {

    /**
     * @param id Producer's ID.
     * @return Producer found by given ID.
     */
    fun find(id: String): MediatorProducer
}
