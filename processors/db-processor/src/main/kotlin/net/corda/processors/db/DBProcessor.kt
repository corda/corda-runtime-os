package net.corda.processors.db

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `DBWorker`. */
@Component(service = [DBProcessor::class])
class DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    @Suppress("unused_parameter")
    fun start(instanceId: Int, config: SmartConfig) {
        logger.info("DB processor starting.")
    }

    fun stop() {
        logger.info("DB processor stopping.")
    }
}