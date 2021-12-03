package net.corda.processors.db

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/**
 * The processor for a `DBWorker`.
 *
 * @property config The configuration for the processor.
 * @property onStatusUpCallback Callback to set the processor's status to `LifecycleStatus.UP`.
 * @property onStatusDownCallback Callback to set the processor's status to `LifecycleStatus.DOWN`.
 * @property onStatusErrorCallback Callback to set the processor's status to `LifecycleStatus.ERROR`.
 */
@Component(service = [DBProcessor::class])
class DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    var config: SmartConfig? = null
    var onStatusUpCallback: (() -> Unit)? = null
    var onStatusDownCallback: (() -> Unit)? = null
    var onStatusErrorCallback: (() -> Unit)? = null

    fun start() {
        logger.info("DB processor starting.")
        onStatusUpCallback?.invoke()
    }

    fun stop() {
        logger.info("DB processor stopping.")
        onStatusDownCallback?.invoke()
    }
}