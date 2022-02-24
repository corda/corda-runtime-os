package net.corda.chunking.db

import net.corda.v5.base.exceptions.CordaRuntimeException

open class ChunkWriteException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)
