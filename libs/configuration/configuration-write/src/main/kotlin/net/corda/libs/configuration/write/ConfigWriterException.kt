package net.corda.libs.configuration.write

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [ConfigWriter]. */
class ConfigWriterException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)