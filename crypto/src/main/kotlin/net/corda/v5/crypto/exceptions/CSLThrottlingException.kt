package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
abstract class CSLThrottlingException : CryptoServiceLibraryException {
    constructor(message: String) : super(message, isRecoverable = true)

    constructor(message: String, cause: Throwable?) : super(message, cause, isRecoverable = true)

    abstract fun getBackoff(attempt: Int, currentBackoffMillis: Long): Long
}