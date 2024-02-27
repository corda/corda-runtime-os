package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException
import java.lang.Exception

class LiquibaseDiffCheckFailedException(val reason: String, val exception: Exception) : CordaRuntimeException(reason, exception)
