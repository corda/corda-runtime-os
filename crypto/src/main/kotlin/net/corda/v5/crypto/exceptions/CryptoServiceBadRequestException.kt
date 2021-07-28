package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class CryptoServiceBadRequestException : CryptoServiceException {
    constructor(message: String) : super(message, false)
    constructor(message: String, cause: Throwable) : super(message, cause, false)
}
