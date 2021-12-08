package net.corda.processors.db.internal.config.writeservice

import net.corda.v5.base.exceptions.CordaRuntimeException

class ConfigWriteServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)