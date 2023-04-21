package net.corda.kryoserialization

import net.corda.v5.base.exceptions.CordaRuntimeException

class CordaKryoException(
    message: String,
    cause: ClassNotFoundException? = null
) : CordaRuntimeException(message, cause)
