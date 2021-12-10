package net.corda.processors.db.internal.db

import net.corda.v5.base.exceptions.CordaRuntimeException

class DBWriteException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)