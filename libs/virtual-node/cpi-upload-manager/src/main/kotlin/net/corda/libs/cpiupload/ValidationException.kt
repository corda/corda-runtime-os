package net.corda.libs.cpiupload

/**
 * Exception type that is thrown when CPI validation fails.
 *
 * This exception is passed via a kafka envelope message and then
 * "checked" in the rpc ops layer when received.
 */
class ValidationException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, ex: Exception) : super(message, ex)
}
