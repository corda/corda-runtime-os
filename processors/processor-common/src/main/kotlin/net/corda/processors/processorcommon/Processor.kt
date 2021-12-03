package net.corda.processors.processorcommon

import net.corda.libs.configuration.SmartConfig

/**
 * The base interface for processors.
 *
 * @property config The configuration for the processor.
 * @property onStatusUpCallback Callback to set the processor's status to `LifecycleStatus.UP`.
 * @property onStatusDownCallback Callback to set the processor's status to `LifecycleStatus.DOWN`.
 * @property onStatusErrorCallback Callback to set the processor's status to `LifecycleStatus.ERROR`.
 */
interface Processor {
    var config: SmartConfig?
    var onStatusUpCallback: (() -> Unit)?
    var onStatusDownCallback: (() -> Unit)?
    var onStatusErrorCallback: (() -> Unit)?

    /** Initialises the [Processor]. */
    fun initialise(
        config: SmartConfig,
        setStatusToUp: () -> Unit,
        setStatusToDown: () -> Unit,
        setStatusToError: () -> Unit
    ) {
        this.config = config
        onStatusUpCallback = setStatusToUp
        onStatusDownCallback = setStatusToDown
        onStatusErrorCallback = setStatusToError
    }

    /** Starts the [Processor]. */
    fun start()

    /** Stops the [Processor]. */
    fun stop()
}