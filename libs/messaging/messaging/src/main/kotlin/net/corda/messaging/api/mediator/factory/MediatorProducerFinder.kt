package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MediatorProducer

/**
 * Mediator producer finder is used to access [MediatorProducer] by its name.
 */
fun interface MediatorProducerFinder {

    /**
     * @param name Producer's name.
     * @return Producer found by given name.
     */
    fun find(name: String): MediatorProducer
}
