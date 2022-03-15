package net.corda.chunking.db.impl.validation

import net.corda.chunking.RequestId

fun interface CpiValidator {
    /**
     * Validate a CPI.
     *
     * @throws [ValidationException] containing failure information
     */
    fun validate(requestId: RequestId)
}
