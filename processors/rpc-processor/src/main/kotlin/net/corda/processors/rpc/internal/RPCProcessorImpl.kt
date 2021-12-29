package net.corda.processors.rpc.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.processors.rpc.RPCProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `RPCWorker`. */
@Component(service = [RPCProcessor::class])
@Suppress("Unused")
class RPCProcessorImpl: RPCProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(config: SmartConfig) {
        logger.info("RPC processor starting.")
    }

    override fun stop() {
        logger.info("RPC processor stopping.")
    }
}