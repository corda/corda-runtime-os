package net.corda.db.connection.manager

import net.corda.v5.base.exceptions.CordaRuntimeException

class DBConfigurationException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)