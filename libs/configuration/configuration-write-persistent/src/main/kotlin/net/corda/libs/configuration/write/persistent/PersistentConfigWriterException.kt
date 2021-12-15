package net.corda.libs.configuration.write.persistent

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [PersistentConfigWriter]. */
class PersistentConfigWriterException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)