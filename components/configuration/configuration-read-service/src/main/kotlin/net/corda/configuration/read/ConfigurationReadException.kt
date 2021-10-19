package net.corda.configuration.read

import net.corda.v5.base.exceptions.CordaRuntimeException

class ConfigurationReadException(message: String, exception: Exception? = null)
    : CordaRuntimeException(message, exception)
