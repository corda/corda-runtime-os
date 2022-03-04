package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.data.chunking.UploadFileStatus

fun interface CpiValidator {
    /**
     * Validate a CPI (details are implementation dependent).
     *
     * @return [UploadFileStatus] enumeration indicating success or type of failure
     */
    fun validate(requestId : RequestId) : UploadFileStatus
}
