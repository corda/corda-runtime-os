package net.corda.v5.base.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
interface CordaThrowable {
    var originalExceptionClassName: String?
    val originalMessage: String?
    fun setMessage(message: String?)
    fun setCause(cause: Throwable?)
    fun addSuppressed(suppressed: Array<Throwable>)
}