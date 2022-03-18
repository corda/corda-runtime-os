package net.corda.chunking.db.impl.validation

import net.corda.chunking.RequestId
import net.corda.v5.crypto.SecureHash

fun interface CpiValidator {
    /**
     * Validate a CPI.
     *
     * @throws [ValidationException] containing failure information
     *
     * @return checksum of the upload CPI file.
     */
    fun validate(requestId: RequestId) : SecureHash
}
