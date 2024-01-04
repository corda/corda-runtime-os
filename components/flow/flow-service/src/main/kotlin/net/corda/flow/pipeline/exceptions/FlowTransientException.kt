package net.corda.flow.pipeline.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * The [FlowTransientException] should only be thrown for transient errors that affect the flow engine and any of its
 * dependant services, it will cause the pipeline execution to be automatically retried at source.
 */
class FlowTransientException(override val message: String, cause: Throwable? = null) :
    CordaRuntimeException(message, cause)
