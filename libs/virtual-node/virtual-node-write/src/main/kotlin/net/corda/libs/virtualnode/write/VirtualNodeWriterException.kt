package net.corda.libs.virtualnode.write

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [VirtualNodeWriter]. */
class VirtualNodeWriterException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)