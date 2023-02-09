package net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception

import java.lang.Exception
import net.corda.v5.base.exceptions.CordaRuntimeException

class MigrationsFailedException(val reason: String, val exception: Exception) : CordaRuntimeException(reason, exception)