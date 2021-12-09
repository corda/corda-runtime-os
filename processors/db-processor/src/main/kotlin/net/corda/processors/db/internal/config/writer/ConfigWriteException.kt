package net.corda.processors.db.internal.config.writer

import net.corda.v5.base.exceptions.CordaRuntimeException

class ConfigWriteException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)