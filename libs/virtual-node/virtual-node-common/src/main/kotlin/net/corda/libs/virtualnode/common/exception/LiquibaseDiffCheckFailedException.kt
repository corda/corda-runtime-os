package net.corda.libs.virtualnode.common.exception

import java.lang.Exception
import net.corda.v5.base.exceptions.CordaRuntimeException

class LiquibaseDiffCheckFailedException(val reason: String, val exception: Exception) : CordaRuntimeException(reason, exception)