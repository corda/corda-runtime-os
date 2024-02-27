package net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception

import net.corda.v5.base.exceptions.CordaRuntimeException
import java.lang.Exception

class MigrationsFailedException(val reason: String, val exception: Exception) : CordaRuntimeException(reason, exception)
