package net.corda.libs.configuration.secret

import net.corda.v5.base.exceptions.CordaRuntimeException

class SecretsConfigurationException(message: String, cause: Throwable? = null):
    CordaRuntimeException(message, cause)