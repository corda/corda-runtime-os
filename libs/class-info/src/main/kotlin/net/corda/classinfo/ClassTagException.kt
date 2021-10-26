package net.corda.classinfo

/** Thrown if an exception occurs related to retrieval of class information. */
class ClassTagException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)