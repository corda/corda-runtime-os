package net.corda.virtualnode.write.db.impl.writer

import net.corda.v5.base.exceptions.CordaRuntimeException

class VirtualNodeDbException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)