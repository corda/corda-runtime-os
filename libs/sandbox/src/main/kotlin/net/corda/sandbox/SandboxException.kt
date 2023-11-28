package net.corda.sandbox

/** Thrown if an exception occurs related to sandbox management. */
class SandboxException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
