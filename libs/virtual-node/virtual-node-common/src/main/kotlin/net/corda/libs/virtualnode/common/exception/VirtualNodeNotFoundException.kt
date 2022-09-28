package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Virtual node not found
 */
class VirtualNodeNotFoundException(message: String) : CordaRuntimeException(message)