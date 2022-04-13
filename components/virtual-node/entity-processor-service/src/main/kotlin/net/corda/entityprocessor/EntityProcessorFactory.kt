package net.corda.entityprocessor

import net.corda.libs.configuration.SmartConfig

interface EntityProcessorFactory {
    /**
     * Create a new entity processor.
     *
     * This should be called from/wired into the db-processor start up.
     */
    fun create(config: SmartConfig): EntityProcessor
}
