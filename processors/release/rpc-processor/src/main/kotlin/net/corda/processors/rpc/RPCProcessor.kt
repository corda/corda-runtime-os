package net.corda.processors.rpc

import net.corda.libs.configuration.SmartConfig
import net.corda.processors.processorcommon.Processor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The processor for a `RPCWorker`. */
@Component(service = [RPCProcessor::class])
class RPCProcessor : Processor {
    private companion object {
        val logger = contextLogger()
    }

    override var config: SmartConfig? = null
    override var onStatusUpCallback: (() -> Unit)? = null
    override var onStatusDownCallback: (() -> Unit)? = null
    override var onStatusErrorCallback: (() -> Unit)? = null

    override fun start() {
        logger.info("RPC processor starting.")
        onStatusUpCallback?.invoke()
    }

    override fun stop() {
        logger.info("RPC processor stopping.")
        onStatusDownCallback?.invoke()
    }
}