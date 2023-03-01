package net.corda.configuration.rest.impl.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [net.corda.configuration.rest.impl.v1.ConfigRestResourceImpl]. */
class ConfigRestResourceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)