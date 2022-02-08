package net.corda.libs.cpiupload

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown from within CpiUploadManager management operations.
 */
class CpiUploadManagerException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)