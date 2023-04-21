package net.corda.libs.packaging.core.exception

/** Thrown if an error occurs while resolving CPK dependencies. */
open class DependencyResolutionException(message: String, cause: Throwable? = null) : PackagingException(message, cause)