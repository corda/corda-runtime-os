package net.corda.packaging

/** Thrown if an exception occurs while reading a CPK/CPB file. */
open class PackagingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown if an exception occurs while parsing CPK dependencies. */
class DependencyMetadataException(message: String, cause: Throwable? = null) : PackagingException(message, cause)

/** Thrown if an exception occurs while parsing DependencyConstraints. */
class LibraryMetadataException(message: String, cause: Throwable? = null) : PackagingException(message, cause)

/** Thrown if an exception occurs while validating integrity of library files contained in a CPK's lib folder. */
class LibraryIntegrityException(message: String, cause: Throwable? = null) : PackagingException(message, cause)

/** Thrown if an exception occurs while parsing main cordapp Manifest . */
class CordappManifestException(message: String, cause: Throwable? = null) : PackagingException(message, cause)

/** Thrown if an error occurs while resolving CPK dependencies. */
open class DependencyResolutionException(message: String, cause: Throwable? = null) : PackagingException(message, cause)

/** Thrown if an invalid jar signature is detected. */
open class InvalidSignatureException(message: String, cause: Throwable? = null) : PackagingException(message, cause)