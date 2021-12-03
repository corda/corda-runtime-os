package net.corda.processors.rpc

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `RPCWorker`. */
@Component(service = [RPCProcessor::class])
class RPCProcessor {
    private companion object {
        val logger = contextLogger()
    }

    var config: SmartConfig? = null

    fun start() {
        logger.info("RPC processor starting.")
    }

    fun stop() {
        logger.info("RPC processor stopping.")
    }
}