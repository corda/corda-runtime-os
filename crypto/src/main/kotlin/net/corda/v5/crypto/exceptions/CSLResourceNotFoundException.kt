package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class CSLResourceNotFoundException : CryptoServiceLibraryException {
    constructor(message: String) : super(message, isRecoverable = false)

    constructor(message: String, cause: Throwable?) : super(message, cause, isRecoverable = false)

    constructor(message: String, cause: Throwable?, isRecoverable: Boolean) : super(message, cause, isRecoverable)
}