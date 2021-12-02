package net.corda.processors.rpc

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/**
 * The processor for a `RPCWorker`.
 *
 * @property config The configuration for the processor.
 * @property onStatusUpCallback Callback to set the processor's status to `LifecycleStatus.UP`.
 * @property onStatusDownCallback Callback to set the processor's status to `LifecycleStatus.DOWN`.
 * @property onStatusErrorCallback Callback to set the processor's status to `LifecycleStatus.ERROR`.
 */
@Component(service = [RPCProcessor::class])
class RPCProcessor : Lifecycle {
    private companion object {
        val logger = contextLogger()
    }

    override var isRunning = false
    var config: SmartConfig? = null
    var onStatusUpCallback: (() -> Unit)? = null
    var onStatusDownCallback: (() -> Unit)? = null
    var onStatusErrorCallback: (() -> Unit)? = null

    override fun start() {
        logger.info("RPC processor starting.")
        onStatusUpCallback?.invoke()
        isRunning = true
    }

    override fun stop() {
        logger.info("RPC processor stopping.")
        onStatusDownCallback?.invoke()
        isRunning = false
    }
}