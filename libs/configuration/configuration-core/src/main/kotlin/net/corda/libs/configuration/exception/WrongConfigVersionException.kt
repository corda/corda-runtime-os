package net.corda.libs.configuration.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Indicates that a configuration management request's version did not match the current version. */
class WrongConfigVersionException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)
