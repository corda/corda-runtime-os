package net.corda.libs.packaging.core.exception

/** Thrown if an exception occurs while parsing main cordapp Manifest . */
class CordappManifestException(message: String, cause: Throwable? = null) : PackagingException(message, cause)