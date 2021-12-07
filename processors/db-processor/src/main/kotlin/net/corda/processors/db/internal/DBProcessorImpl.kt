package net.corda.processors.db.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `DBWorker`. */
@Suppress("Unused")
@Component(service = [DBProcessor::class])
class DBProcessorImpl: DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(instanceId: Int, topicPrefix: String, config: SmartConfig) {
        logger.info("DB processor starting.")
    }

    override fun stop() {
        logger.info("DB processor stopping.")
    }
}