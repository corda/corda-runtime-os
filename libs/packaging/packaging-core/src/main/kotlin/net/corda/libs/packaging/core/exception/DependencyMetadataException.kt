package net.corda.libs.packaging.core.exception

/** Thrown if an exception occurs while parsing CPK dependencies. */
class DependencyMetadataException(message: String, cause: Throwable? = null) : PackagingException(message, cause)