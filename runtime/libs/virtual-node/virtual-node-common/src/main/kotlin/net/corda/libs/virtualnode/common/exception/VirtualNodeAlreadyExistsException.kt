package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Virtual node already exists
 */
class VirtualNodeAlreadyExistsException(message: String) : CordaRuntimeException(message)