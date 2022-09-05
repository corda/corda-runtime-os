package net.corda.flow.external.events.impl.factory

import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.pipeline.exceptions.FlowFatalException

/**
 * [ExternalEventFactoryMap] holds all the registered [ExternalEventFactory] implementations so that they can be
 * retrieved at runtime.
 */
interface ExternalEventFactoryMap {

    /**
     * Gets the [ExternalEventFactory] with the passed in [factoryClassName].
     *
     * @param factoryClassName The class name of the factory that should be retrieved.
     *
     * @return The [ExternalEventFactory] with the passed in [factoryClassName].
     *
     * @throws FlowFatalException if there is no matched [ExternalEventFactory] for [factoryClassName].
     */
    fun get(factoryClassName: String): ExternalEventFactory<Any, Any, *>
}