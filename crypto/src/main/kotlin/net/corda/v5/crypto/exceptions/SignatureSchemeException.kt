package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
open class SignatureSchemeException(message: String) : CryptoServiceLibraryException(message)