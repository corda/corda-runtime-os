package net.corda.libs.packaging.core.exception

/** Thrown if an exception occurs while validating integrity of library files contained in a CPK's lib folder. */
class LibraryIntegrityException(message: String, cause: Throwable? = null) : PackagingException(message, cause)