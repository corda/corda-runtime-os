package net.corda.chunking.read

import net.corda.v5.base.exceptions.CordaRuntimeException

open class CpiUploadException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)
