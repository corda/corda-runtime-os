package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
open class CryptoServiceException : CryptoServiceLibraryException {
    var isRecoverable = true
        private set

    constructor(message: String) : super(message)
    constructor(message: String, isRecoverable: Boolean) : super(message) {
        this.isRecoverable = isRecoverable
    }

    constructor(message: String, cause: Throwable?) : super(message, cause)
    constructor(message: String, cause: Throwable?, isRecoverable: Boolean) : super(message, cause) {
        this.isRecoverable = isRecoverable
    }
}