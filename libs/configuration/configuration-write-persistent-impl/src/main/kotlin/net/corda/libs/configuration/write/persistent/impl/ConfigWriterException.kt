package net.corda.libs.configuration.write.persistent.impl

import net.corda.v5.base.exceptions.CordaRuntimeException

/** For exceptions related to the persistent config writer. */
class ConfigWriterException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)