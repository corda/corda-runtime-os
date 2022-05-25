package net.corda.libs.packaging.core.exception

/** Thrown if an invalid jar signature is detected. */
open class InvalidSignatureException(message: String, cause: Throwable? = null) : PackagingException(message, cause)