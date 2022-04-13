package net.corda.v5.application.configuration

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown if an exception occurs in accessing or parsing cordapp configuration
 */
class CordappConfigException(msg: String, e: Throwable) : CordaRuntimeException(msg, e)
