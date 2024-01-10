package net.corda.virtualnode.write.db

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [VirtualNodeWriteService]. */
class VirtualNodeWriteServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)