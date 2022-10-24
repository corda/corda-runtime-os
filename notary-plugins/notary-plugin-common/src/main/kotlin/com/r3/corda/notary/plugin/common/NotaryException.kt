package com.r3.corda.notary.plugin.common

import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash

/**
 * Exception thrown by the notary service if any issues are encountered while trying to commit a transaction. The
 * underlying [error] specifies the cause of failure.
 *
 * TODO This exception used to inherit from `FlowException` but that type doesn't exist anymore, will
 *  `CordaRuntimeException` work?
 */
class NotaryException(
    /** Cause of notarisation failure. */
    val error: UniquenessCheckError,
    /** Id of the transaction to be notarised. Can be _null_ if an error occurred before the id could be resolved. */
    val txId: SecureHash?
) : CordaRuntimeException("Unable to notarise transaction ${txId ?: "<Unknown>"} : $error") {

    constructor(error: UniquenessCheckError) : this(error, txId = null)
}
