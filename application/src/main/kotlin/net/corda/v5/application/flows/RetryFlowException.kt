package net.corda.v5.application.flows

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * This exception will cause the flow to retry from its previous checkpoint.
 */
open class RetryFlowException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause) {
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
    constructor() : this(null, null)
}