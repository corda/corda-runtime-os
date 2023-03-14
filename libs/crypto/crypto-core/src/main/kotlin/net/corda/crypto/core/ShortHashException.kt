package net.corda.crypto.core

/** Exception thrown if creation of a [ShortHash] fails */
class ShortHashException(message: String?, cause: Throwable? = null) : Exception(message, cause)
