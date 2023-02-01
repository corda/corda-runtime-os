package net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class VirtualNodeStateException(message: String) : CordaRuntimeException(message)