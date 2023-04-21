package net.corda.libs.packaging.core.exception

/** Thrown if an exception occurs while parsing DependencyConstraints. */
class LibraryMetadataException(message: String, cause: Throwable? = null) : PackagingException(message, cause)