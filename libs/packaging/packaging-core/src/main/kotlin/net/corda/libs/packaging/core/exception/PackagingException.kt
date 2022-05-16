package net.corda.libs.packaging.core.exception

/** Thrown if an exception occurs while reading a CPK/CPB file. */
open class PackagingException(message: String, cause: Throwable? = null) : Exception(message, cause)