package net.corda.processors.flow.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.processors.flow.FlowProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `FlowWorker`. */
@Component(service = [FlowProcessor::class])
@Suppress("Unused")
class FlowProcessorImpl: FlowProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(instanceId: Int, topicPrefix: String, config: SmartConfig) {
        logger.info("Flow processor starting.")
    }

    override fun stop() {
        logger.info("Flow processor stopping.")
    }
}