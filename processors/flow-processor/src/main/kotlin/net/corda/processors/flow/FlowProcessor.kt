package net.corda.processors.flow

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `FlowWorker`. */
@Component(service = [FlowProcessor::class])
class FlowProcessor {
    private companion object {
        val logger = contextLogger()
    }

    @Suppress("unused_parameter")
    fun start(instanceId: Int, config: SmartConfig) {
        logger.info("Flow processor starting.")
    }

    fun stop() {
        logger.info("Flow processor stopping.")
    }
}