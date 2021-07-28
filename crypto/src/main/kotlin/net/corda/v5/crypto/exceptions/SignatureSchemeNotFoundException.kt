package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class SignatureSchemeNotFoundException(message: String) : SignatureSchemeException(message)