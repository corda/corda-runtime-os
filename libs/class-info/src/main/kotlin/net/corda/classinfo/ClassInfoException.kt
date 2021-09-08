package net.corda.classinfo

/** Thrown if an exception occurs related to retrieval of class information. */
class ClassInfoException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)