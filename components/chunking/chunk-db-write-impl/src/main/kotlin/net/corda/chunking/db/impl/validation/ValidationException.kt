package net.corda.chunking.db.impl.validation

/** Simple wrapper for validation exceptions */
internal class ValidationException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, ex: Exception) : super(message, ex)
}
