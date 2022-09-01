package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * CPI not found
 */
class CpiNotFoundException(message: String) : CordaRuntimeException(message)