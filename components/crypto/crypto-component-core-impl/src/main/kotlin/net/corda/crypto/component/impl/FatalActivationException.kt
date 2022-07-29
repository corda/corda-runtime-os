package net.corda.crypto.component.impl

class FatalActivationException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)