package net.corda.libs.packaging.core.exception

/** Thown if a FormatVersion is unknown */
class UnknownFormatVersionException(message: String, cause: Throwable? = null) : PackagingException(message, cause)