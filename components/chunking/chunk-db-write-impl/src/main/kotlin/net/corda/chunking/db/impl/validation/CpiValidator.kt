package net.corda.chunking.db.impl.validation

import net.corda.chunking.RequestId
import net.corda.v5.crypto.SecureHash

interface CpiValidator {
    /*
     * Notify a chunk received
     */

    fun notifyChunkReceived(requestId: RequestId)

    /*
     * Notify an error in chunking
     */

    fun notifyChunkError(requestId: RequestId, e: Exception)
    /**
     * Validate a CPI.
     *
     * @throws [ValidationException] containing failure information
     *
     * @return checksum of the upload CPI file.
     */
    fun validate(requestId: RequestId) : SecureHash
}
