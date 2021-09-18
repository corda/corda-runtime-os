package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
open class CryptoServiceException : CryptoServiceLibraryException {

    constructor(message: String) : super(message)

    constructor(message: String, isRecoverable: Boolean) : super(message, isRecoverable)

    constructor(message: String, cause: Throwable?) : super(message, cause)

    constructor(message: String, cause: Throwable?, isRecoverable: Boolean) : super(message, cause, isRecoverable)
}