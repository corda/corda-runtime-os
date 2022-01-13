package net.corda.libs.configuration.write

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [ConfigWriter]. */
open class ConfigWriterException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)

/** Indicates that a configuration management request's version did not match the current version. */
class WrongVersionException(message: String, e: Exception? = null): ConfigWriterException(message, e)