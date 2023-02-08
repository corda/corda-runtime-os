package net.corda.libs.cpiupload

/**
 * Exception type that is thrown when CPI validation fails.
 *
 * This exception is passed via a kafka envelope message and then
 * "checked" in the rest ops layer when received.
 */
class ValidationException(message: String,  val requestId: String?, ex:Exception?=null) : Exception(message, ex)
