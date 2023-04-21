package net.corda.libs.configuration.secret

/**
 * Exception thrown to indicate a problem with using configuration items that are marked as secrets.
 */
class SecretsConfigurationException(message: String, cause: Throwable? = null):
    Exception(message, cause)