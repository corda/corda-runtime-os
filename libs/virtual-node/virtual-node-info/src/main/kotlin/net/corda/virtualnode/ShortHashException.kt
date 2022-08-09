package net.corda.virtualnode

/** Exception thrown if creation of a [ShortHash] fails */
class ShortHashException(message: String?, cause: Throwable? = null) : Exception(message, cause)
