package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

@CordaSerializable
open class CryptoServiceLibraryException : CordaRuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
}
