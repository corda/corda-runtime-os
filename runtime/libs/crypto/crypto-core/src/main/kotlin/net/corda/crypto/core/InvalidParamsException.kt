package net.corda.crypto.core

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Thrown when an invalid/incorrect parameter was used and failed the validation. */
class InvalidParamsException(message: String): CordaRuntimeException(message)